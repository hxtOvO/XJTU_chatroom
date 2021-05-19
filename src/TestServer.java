import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Scanner;
import java.util.StringTokenizer;
import java.util.Vector;
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
    //文件服务器,有一个
    private ServerSocket fileServer = null;
    //服务端的Socket,是个临时变量
    private Socket soc = null;
    //输出流的map,需要线程安全
    private ConcurrentHashMap<Integer,PrintWriter> allOut;
    //线程池
    private ExecutorService es = null;
    //服务器的数据库
    SQLServer DBServer = null;
    //构造函数,创建一个ServerSocket,监听10170端口,连接队列为3,同时创建输出流队列
    public TestServer() {
        try {
            server = new ServerSocket(10170,30);
            fileServer = new ServerSocket(10180,30);
            allOut = new ConcurrentHashMap<>();
            DBServer = new SQLServer();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    //获取服务端的数据库(存放用户认证信息)
    public SQLServer GetServerDB(){return DBServer;}
    //接收一个客户端的连接,返回一个Socket
    public Socket GetSoc(ServerSocket se) {
        try {
            System.out.println("Server established.");
            boolean flag = true;
            while(flag)
            {
                System.out.println("Waiting for connection...");
                soc = se.accept();
                if(soc!=null) flag=false;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return soc;
    }
    //将输出流放到list中
    public synchronized void addOut(int num,PrintWriter pw){
        allOut.put(num,pw);
    }
    //将输出流从list中移除
    public synchronized void removeOut(int num){
        allOut.remove(num);
    }
    //广播文本到list中的所有流(考虑不转发给自己)
    public synchronized void send2All(String s,Integer num){
        for (Integer i:allOut.keySet()){
            if(!i.equals(num)){
                allOut.get(i).println(s);
            }
        }
    }
    //转发给指定Socket
    public synchronized void send2One(String str,Integer num){
        allOut.get(num).println(str);
    }
    //start方法,服务端从这里开始工作,使用线程池实现多线程连接(?)
    public void start(){
        try {
            //线程池最大并发数为4，多则在等待队列等待
            es = Executors.newFixedThreadPool(4);
            //ThreadPoolExecutor tpe = ((ThreadPoolExecutor) es);

            es.submit(new CloseThread());
            int userCount=1;//用于标识连接的Client
            do {
                //向线程池中添加线程
                es.submit(new Handler(userCount, GetSoc(server)));
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
        public boolean LogIn(){
            System.out.println("Waiting for login info...");
            if(s.isConnected()){
                String str = GetOnce();
                if(str.equals("Error")){return false;}
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
                    return true;
                }
                else if(Auth.equals(0)) {
                    //新用户的处理
                    GetServerDB().AddClient(name, pw, num);
                    send2One("1",num);
                    send2All(name+" log in.",num);
                    return true;
                }
                else if(Auth.equals(-1)){
                    send2One("0",num);
                    return false;
                }
                else{
                    System.out.println("Database Error.");
                    return false;
                }
            }
            System.out.println("Log in over.");
            return false;
        }
        //登录成功后,发送一部分数据
        public void SendStatus(){
            String str = GetOnce();
            while(!str.equals("request_for_onlineUser")){
                str = GetOnce();
            }
            send2One(GetServerDB().GetOnlineStatus(name), num);
            int count = DirScan(name);//发送在服务器的离线文件数目
            send2One("@"+name+":/Files:"+count, num);
            if(count!=0){
                SendFileList(name);
            }
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
        //发送离线文件列表
        public void SendFileList(String name){
            StringBuilder msg = new StringBuilder("@server:/FileList:");
            String filePath = "/home/kkuan/Java/com/Clients/";
            Path p = Paths.get(filePath+name);
            try {
                DirectoryStream<Path> stream = Files.newDirectoryStream(p);
                for (Path file:stream){
                    msg.append(file.getFileName());
                    msg.append("~");
                    msg.append(Files.size(file));
                    msg.append("`");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            send2One(msg.toString(),num);

        }
        //处理消息
        public void MsgHandler(Socket s, int i) {
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
                            //登出
                            if(msg.equals("/logout")){
                                send2One(msg,i);
                                break;
                            }
                            //分割消息
                            String[] tMsg = new String[2];
                            //msg->@name:msg
                            //msg->@$gName:msg
                            tMsg[0] = msg.substring(1).substring(0,msg.indexOf(":")-1);//name
                            tMsg[1] = msg.substring(msg.indexOf(":"));//msg
                            if(tMsg[1].startsWith("/OnlineFile", 1)){
                                tMsg[1] = tMsg[1]+"/IP:111.229.120.197";
                                OnlineFile of = new OnlineFile();
                                Thread t = new Thread(of);
                                t.start();
                            }
                            if(tMsg[1].startsWith("/OfflineFile", 1)){
                                //@name:/OfflineFile:fileName/FileLength:fileLength
                                //ss[2]:FileLength
                                //String length = msg.substring(msg.indexOf(":")+1).split("/")[2].split(":")[1];
                                String dst = msg.substring(1).split(":")[0];
                                GetFile getFile = new GetFile(dst);
                                Thread t = new Thread(getFile);
                                t.start();
                                tMsg[1] = ":FileExist:" + msg.split("/")[1].split(":")[1];
                            }
                            if(tMsg[1].startsWith("/FileList",1)){
                                //@server:/FileList
                                SendFileList(name);
                            }
                            if(tMsg[1].startsWith("/Download",1)){
                                //@server:/Download/filename
                                SendFile sendFile = new SendFile(name,tMsg[1].split("/")[2]);
                                Thread t = new Thread(sendFile);
                                t.start();
                            }
                            if(tMsg[1].startsWith("/GroupCreate",1) ||
                                    tMsg[1].startsWith("/GroupJoin",1)){
                                //@server:/GroupCreate:{groupName}
                                String gName = tMsg[1].split(":")[2];
                                GetServerDB().GroupConnect(gName,name);
                                send2One("$"+gName+" log in.",num);
                            }
                            if(tMsg[0].startsWith("$")){
                                //@$gName:msg
                                send2Group(tMsg[0].substring(1),tMsg[1],name);
                            }
                            else{
                                Integer des = -1;
                                if(!tMsg[0].equals("server")){
                                    des = DBServer.FindNum(tMsg[0]);
                                }
                                if(des>=0) { send2One("@"+ name + tMsg[1],des); }
                            }
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
                if(LogIn()){
                    SendStatus();
                    MsgHandler(s, num);
                } else {
                    soc.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            System.out.println("End Task: " + num);
        }
    }
    //处理在线文件
    private class OnlineFile implements Runnable{
        private Socket sender = null;
        private Socket getter = null;
        @Override
        public void run(){
            try {
                sender = GetSoc(fileServer);
                getter = GetSoc(fileServer);
                DataInputStream sDis = new DataInputStream(sender.getInputStream());
                DataOutputStream sDos = new DataOutputStream(sender.getOutputStream());

                DataInputStream gDis = new DataInputStream(getter.getInputStream());
                DataOutputStream gDos = new DataOutputStream(getter.getOutputStream());

                gDos.writeUTF(sDis.readUTF());
                gDos.flush();
                if(gDis.readUTF().equals("ok")){
                    sDos.writeUTF("ok");
                    sDos.flush();
                    sDos.writeLong(gDis.readLong());
                    sDos.flush();
                    gDos.writeLong(sDis.readLong());
                    gDos.flush();
                    if(sDis.readUTF().equals("ok")){
                        gDos.writeUTF("ok");
                        gDos.flush();
                        ByteBuffer buffer = ByteBuffer.allocate(1024);
                        System.out.println("Start.");
                        while(sDis.read(buffer.array(),0,buffer.remaining())!=-1){
                            gDos.write(buffer.array());
                            buffer.clear();
                        }
                        System.out.println("Finish.");
                    }
                }

            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    getter.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

        }
    }

    //接收离线文件的类
    private class GetFile implements Runnable{
        private String path = "/home/kkuan/Java/com/Clients/";
        private final Socket fileSocket;

        @Override
        public void run(){
            try{
                if(soc.isConnected()){
                    DataInputStream dis = new DataInputStream(fileSocket.getInputStream());
                    DataOutputStream dos = new DataOutputStream(fileSocket.getOutputStream());
                    String fileName = dis.readUTF();
                    dos.writeUTF("ok");
                    dos.flush();
                    Path p = Paths.get(path+"/"+fileName+".temp");
                    File file = new File(path+"/"+fileName+".temp");
                    if(!Files.exists(p)){
                        Files.createFile(p);
                    }
                    RandomAccessFile rad = new RandomAccessFile(file, "rw");
                    long recSize = 0;
                    if (Files.exists(p)&&Files.isRegularFile(p)) {
                        recSize = Files.size(p);
                    }
                    dos.writeLong(recSize);
                    dos.flush();
                    dis.readLong();
                    String rsp = dis.readUTF();
                    if (rsp.equals("ok")){
                        rad.seek(recSize);
                        int length;
                        byte[] bytes = new byte[1024];
                        System.out.println("Start get file.");
                        while ((length = dis.read(bytes,0,bytes.length)) !=-1) {
                            rad.write(bytes, 0, length);
                        }
                        System.out.println("Finish");
                        rad.close();
                        Files.move(p, Paths.get(path + "/" + fileName));
                    }
                }
            }catch (IOException e){
                e.printStackTrace();
            }finally {
                try{
                    fileSocket.close();
                }catch(IOException e){
                    e.printStackTrace();
                }
            }
        }

        public GetFile(String n){
            this.path = this.path+n;
            fileSocket = GetSoc(fileServer);
        }
    }
    //发送离线文件的类
    private class SendFile implements Runnable{
        @Override
        public void run(){
            try{
                String filePath = "/home/kkuan/Java/com/Clients/";
                //Path p = Paths.get(filePath +userName+"/"+fileName);
                DataOutputStream dos = new DataOutputStream(fileSocket.getOutputStream());
                DataInputStream dis = new DataInputStream(fileSocket.getInputStream());
                File file = new File(filePath+userName+"/"+fileName);
                RandomAccessFile rad = new RandomAccessFile(file, "r");
                byte[] bytes = new byte[1024];
                dos.writeUTF(fileName);
                dos.flush();
                String rsp = dis.readUTF();
                if(rsp.equals("ok")){
                    long size = dis.readLong();
                    dos.writeLong(rad.length());
                    dos.flush();
                    dos.writeUTF("ok");
                    dos.flush();
                    if(size<rad.length()){
                        //seekableByteChannel.position(size);
                        rad.seek(size);
                        System.out.println("Start send file.");
                        int l;
                        while((l = rad.read(bytes))>0){
                            dos.write(bytes, 0, l);
                            //SwingUtilities.invokeLater(() -> progressBar.setValue(++barOffset));
                            dos.flush();
                        }
                    }
                    System.out.println("finish");
                }
            } catch (IOException e){
                e.printStackTrace();
            }finally {
                try{
                    fileSocket.shutdownOutput();
                }catch(IOException e){
                    e.printStackTrace();
                }
            }
        }

        private final String userName;
        private final String fileName;
        private final Socket fileSocket;

        public SendFile(String un, String fn){
            userName = un;
            fileName = fn;
            fileSocket = GetSoc(fileServer);
        }
    }
    //查一下对应用户的文件夹
    public static int DirScan(String name){
        //新用户 i=0 建一个新文件夹,返回0
        //老用户 i=1 查一下文件夹,返回里面的文件数目
        Path path = Paths.get("/home/kkuan/Java/com/Clients/"+name);
        if(Files.exists(path)){
            try {
                return (int) Files.list(path).count();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            try {
                Files.createDirectory(path);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return 0;
        }
        return -1;
    }
    //给群发消息
    public synchronized void send2Group(String gName, String msg, String sender){
        Vector<Integer> member= GetServerDB().GroupSearch(gName);
        member.remove(GetServerDB().FindNum(sender));
        for(Integer i:member){
            allOut.get(i).println("$"+gName+"@"+sender+msg);
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

