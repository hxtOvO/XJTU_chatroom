import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @description: support Server's multithreading ,broadcast message to all
 *               "/exit" to terminate.
 */
public class TestServer {
    //服务端的ServerSocket,稳定只有一个
    private ServerSocket server = null;
    //服务端的Socket,是个临时变量
    private Socket soc = null;
    //输出流的list,需要线程安全
    private ConcurrentHashMap<Integer,PrintWriter> allOut;
    private ExecutorService es = null;
    //服务器的数据库
    SQLServer DBServer = null;
    //构造函数,创建一个ServerSocket,监听10170端口,连接队列为3,同时创建输出流队列
    public TestServer() {
        try {
            server = new ServerSocket(10170,3);
            allOut = new ConcurrentHashMap<>();
            DBServer = new SQLServer();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    //获取服务端的数据库(存放用户认证信息)
    public SQLServer GetServerDB(){return DBServer;}
    //接收一个客户端的连接,返回一个Socket
    public Socket GetSoc() {
        try {
            System.out.println("Server established.");
            boolean flag = true;
            while(flag)
            {
                System.out.println("Waiting for connection...");
                soc = server.accept();
                if(soc!=null) flag=false;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return soc;
    }
    //将输出流放到list中
    private synchronized void addOut(int num,PrintWriter pw){
        allOut.put(num,pw);
    }
    //将输出流从list中移除
    private synchronized void removeOut(int num){
        allOut.remove(num);
    }
    //广播文本到list中的所有流(考虑不转发给自己)
    private synchronized void send2All(String s,Integer num){
        for (Integer i:allOut.keySet()){
            if(!i.equals(num)){
                allOut.get(i).println(s);
            }
        }
    }
    //转发给指定Socket,暂时只用于登出
    private synchronized void send2One(String str,Integer num){
        PrintWriter pw = allOut.get(num);
        pw.println(str);
    }
    //start方法,服务端从这里开始工作,使用线程池实现多线程连接(?)
    public void start(){
        try {
            //线程池最大并发数为4，多则在等待队列等待
            es = Executors.newFixedThreadPool(4);
            //ThreadPoolExecutor tpe = ((ThreadPoolExecutor) es);

            es.submit(new CloseThread());
            int userCount=0;//用于标识连接的Client
            do {
                //向线程池中添加线程
                es.submit(new Handler(userCount, GetSoc()));
                userCount++;
            } while(true);
            //es.shutdown();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    //main方法,调用start()方法
    public static void main(String[] args) {
        TestServer MainServer = new TestServer();
        MainServer.start();
    }
    //Class Handler是与Client通信的一个子线程
    private class Handler implements Runnable {
        private String name;
        private final int num;
        private final Socket s;
        public Handler(/*String name,*/ int num, Socket s) {
            //this.name = name;
            this.num=num;
            this.s=s;
            PrintWriter pw;
            try{
                OutputStream out = s.getOutputStream();
                OutputStreamWriter osw = new OutputStreamWriter(out, StandardCharsets.UTF_8);
                pw = new PrintWriter(osw,true);
                //添加到服务端的list中
                addOut(num,pw);
            }catch(IOException e){
                e.printStackTrace();
            }

        }

        //登录方法
        public void LogIn(){
            System.out.println("Waiting for login info...");
            if(s.isConnected()){
                String str = GetOnce();
                if(str.equals("Error")){return;}
                StringTokenizer st = new StringTokenizer(str,"`");
                String name = st.nextToken();
                this.name = name;
                String pw = st.nextToken();
                Integer Auth = GetServerDB().LogInAuth(name,pw);
                if(Auth.equals(1)) {
                    //密码正确,回应登录成功,并将状态更改为true
                    GetServerDB().ChangeOnline(name,true,num);
                    send2One("1", num);
                    send2All(name+" log in.",num);
                }
                else if(Auth.equals(0)) {
                    //新用户的处理
                    GetServerDB().AddClient(name, pw, num);
                    send2One("1",num);
                    send2All(name+" log in.",num);
                }
                else if(Auth.equals(-1)){
                    send2One("0",num);
                    try{
                        s.close();
                    }catch (IOException e){
                        e.printStackTrace();
                    }
                }
                else{
                    System.out.println("Database Error.");
                    try{
                        s.close();
                    }catch (IOException e){
                        e.printStackTrace();
                    }
                }
            }
            System.out.println("Log in over.");
        }
        //登录成功后,发送在线用户名单
        public void SendOnlineStatus(){
            String str = GetOnce();
            while(!str.equals("request_for_onlineUser")){
                str = GetOnce();
            }
            send2One(GetServerDB().GetOnlineStatus(), num);
        }
        //一个可以复用的方法,接收一次输入流的数据
        public String GetOnce(){
            try{
                if(s.isConnected()) {
                    InputStream in = s.getInputStream();
                    InputStreamReader isr = new InputStreamReader(in, StandardCharsets.UTF_8);
                    //创建一个缓冲区输入流和一个String
                    BufferedReader br = new BufferedReader(isr);
                    String str = null;
                    while (s.isConnected()) {
                        str = br.readLine();
                        if (str != null) break;
                    }
                    return str;
                }
            }catch (IOException e){e.printStackTrace();}
            return "Error";
        }
        public void MsgHandler(Socket s, int i)
        {
            /* 尝试将获取输入与转发放到一个函数里
             * public void MsgHandler(Socket s, int i)
             * 输出流放在TestServer的list中(广播转发)
             * 私聊考虑使用HashMap,key为客户端名字,value为对应的输出流
             */
            //这个线程的输出流
            try {
                //获取客户端Socket的输出流
                if(s.isConnected()){
                    //创建一个属于线程的输入流,获取客户端的输入
                    InputStream in = s.getInputStream();
                    InputStreamReader isr = new InputStreamReader(in,StandardCharsets.UTF_8);
                    //创建一个缓冲区输入流和一个String
                    BufferedReader br = new BufferedReader(isr);
                    String msg;
                    while(s.isConnected()){
                        msg = br.readLine();
                        if(msg!=null){
                            if(msg.equals("/logout")){
                                send2One(msg,i);
                                break;
                            }
                            String[] tMsg = new String[2];
                            //msg->@name:msg
                            tMsg[0] = msg.substring(1).substring(0,msg.indexOf(":")-1);//name
                            tMsg[1] = msg.substring(msg.indexOf(":"));//msg
                            //@All:msg
//                            if(tMsg[0].equals("All")){ /*send2All("@" + name + ":" + tMsg[1],i);*/ }
//                            else{
                            if(tMsg[1].startsWith("/SendFile", 1)){
                                tMsg[1] = tMsg[1]+"/IP:"+s.getInetAddress().getHostAddress();
                            }
                            Integer des = DBServer.FindNum(tMsg[0]);
                            if(des>=0) { send2One("@"+ name + tMsg[1],des); }
                            //}
                            System.out.println(name + " says: " + msg);
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                //连接断开时,将服务端的Socket close,并且remove对应的pw
                removeOut(i);
                //广播该客户端离线
                GetServerDB().ChangeOnline(name,false,i);
                send2All(name + " log out.",i);
                //关闭Socket
                if(s!=null){
                    try{
                        s.close();
                    }catch (IOException e){
                        e.printStackTrace();
                    }
                }
            }
        }

        @Override
        public void run()
        {
            System.out.println("Start task: " + num);
            try {
                LogIn();
                SendOnlineStatus();
                MsgHandler(s, num);
            } catch (Exception e) {
                e.printStackTrace();
            }
            System.out.println("End Task: " + num);
        }
    }
    //监听服务器端是否关闭的线程--通过键盘输入/exit
    private class CloseThread implements Runnable {
        @Override
        public void run() {
            Scanner sc = new Scanner(System.in);
            while(true){
                if(sc.nextLine().equals("/exit")) {
                    try {
                        send2All("/logout",-1);
                        soc.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    finally {
                        DBServer.ServerClose();
                        es.shutdown();
                    }
                    System.exit(0);
                    break;
                }
            }
        }
    }
}

