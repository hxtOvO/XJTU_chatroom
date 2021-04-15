import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TestServer {
    //服务端的ServerSocket,稳定只有一个
    private ServerSocket server = null;
    //服务端的Socket,是个临时变量
    private Socket soc = null;
    //输出流的list,需要线程安全
    private CopyOnWriteArrayList<PrintWriter> allOut;
    //构造函数,创建一个ServerSocket,监听10170端口,连接队列为3,同时创建输出流队列
    public TestServer()
    {
        try {
            server = new ServerSocket(10170,3);
            allOut = new CopyOnWriteArrayList<>();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    //接收一个客户端的连接,返回一个Socket
    public Socket GetSoc()
    {
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
    private synchronized void addOut(PrintWriter pw){
        allOut.add(pw);
    }
    //将输出流从list中移除
    private synchronized void removeOut(PrintWriter pw){
        allOut.remove(pw);
    }
    //广播文本到list中的所有流(考虑不转发给自己)
    private synchronized void send2All(String s){
        for (PrintWriter pw:allOut){
            pw.println(s);
        }
    }
    //start方法,服务端从这里开始工作,使用线程池实现多线程连接(?)
    public void start(){
        try {
            ExecutorService es = Executors.newFixedThreadPool(4);
            for (int i=0;i<2;i++){
                es.submit(new Handler(i, GetSoc()));
            }
            es.shutdown();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    //main方法,调用start()方法
    public static void main(String[] args)
    {
        TestServer MainServer = new TestServer();
        MainServer.start();
    }

    private class Handler implements Runnable {
        //private final String name;
        private final int num;
        private final Socket s;
        public Handler(/*String name,*/ int num, Socket s) {
            //this.name = name;
            this.num=num;
            this.s=s;
        }

        public void GetMsg(Socket s, int i)
        {
            /* 尝试将获取输入与转发放到一个函数里
             * public void MsgHandler(Socket s, int i)
             * 输出流放在TestServer的list中(广播转发)
             * 私聊考虑使用HashMap,key为客户端名字,value为对应的输出流
             */
            //这个线程的输出流
            PrintWriter pw = null;
            try {
                //获取客户端Socket的输出流
                if(s.isConnected()){
                    OutputStream out = s.getOutputStream();
                    OutputStreamWriter osw = new OutputStreamWriter(out, StandardCharsets.UTF_8);
                    pw = new PrintWriter(osw,true);
                    //添加到服务端的list中
                    addOut(pw);
                    //广播该用户上线(后续可以去掉这个)
                    send2All("Client"+i+" log in.");
                    //创建一个属于线程的输入流,获取客户端的输入
                    InputStream in = s.getInputStream();
                    InputStreamReader isr = new InputStreamReader(in,StandardCharsets.UTF_8);
                    //创建一个缓冲区输入流和一个String
                    BufferedReader br = new BufferedReader(isr);
                    String msg;
                    while(s.isConnected()){
                        msg = br.readLine();
                        if(msg!=null){
                            System.out.println("Client" + i + " says: " + msg);
                            send2All("Client" + i + " says: " + msg);
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                //连接断开时,将服务端的Socket close,并且remove对应的pw
                removeOut(pw);
                //广播该客户端离线
                send2All("Client" + i + " log out.");
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
                GetMsg(s, num);
            } catch (Exception e) {
                e.printStackTrace();
            }
            System.out.println("End Task: " + num);
        }
    }

}

