import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Scanner;

/**
 * @program: XJTU_chatroom
 * @description: only one thread
 * @author: 席卓然
 * @create: 2021-04-13 19:59
 * @version 1.0.0
 **/

public class ChatClient {
    private Socket socket = null;
    private volatile int count = 0;
    /**
     * 客户端的动作：
     * sendMessage/readMessage/connectServer/
     * disconnect
     */

    public ChatClient(){
        try{
            System.out.println("connecting...");
            socket = new Socket("111.229.120.197",10170);//在Client端，需要指定host的ip地址和端口
            System.out.println("connect successfully.");
        } catch (IOException e){
            e.printStackTrace();
        }
    }

    public void start(){
        try {
            Integer login = LogIn();
            if(login.equals(1)){
                GetMsgFromServer getter = new GetMsgFromServer();
                SendMsgToServer sender = new SendMsgToServer();
                Thread tg = new Thread(getter);
                inc();
                Thread ts = new Thread(sender);
                inc();
                tg.start();
                ts.start();
                while (count != 0) Thread.onSpinWait();
                ClientClose();
            }
            else if(login.equals(0)){
                System.out.println("Wrong password.");
            }
            else{
                System.out.println("Failed.");
                ClientClose();
            }

        } catch (Exception e){
            e.printStackTrace();
        }
    }

    public Integer LogIn(){
        Scanner scanner = new Scanner(System.in);
        System.out.print("name:");
        String name = scanner.next();
        System.out.print("password: ");
        String pwd = scanner.next();
        try{
            PrintWriter pw = new PrintWriter(socket.getOutputStream(),true);
            pw.println(name+"`"+pwd);
            System.out.println("logging in ...");
            InputStreamReader isr = new InputStreamReader(socket.getInputStream(),StandardCharsets.UTF_8);
            BufferedReader br = new BufferedReader(isr);
            String str = null;
            while(socket.isConnected()){
                str=br.readLine();
                if(str!=null) break;
            }
            System.out.println("Get");
            if(Objects.equals(str, "You are logged in.")) return 1;
            else if (Objects.equals(str, "Wrong password.")) return 0;
            else return -1;
        }catch (IOException e){
            e.printStackTrace();
        } return -1;
    }

    public synchronized void inc(){
        count++;
    }

    public synchronized void dec(){
        count--;
    }

    public static void main(String[] args) {
        ChatClient MyClient = new ChatClient();
        MyClient.start();
    }

    public void ClientClose(){
        try {
            if(count==0 && socket!=null){
                socket.close();
                System.out.println("Client has disconnected...");
                socket = null;
            }
            else{
                dec();
            }
        } catch (IOException e){
            e.printStackTrace();
        }
    }

    private class SendMsgToServer implements Runnable{
        @Override
        public void run(){
            try {
                //socket = connectServer(port);
                PrintWriter pw = new PrintWriter(socket.getOutputStream(), true);//设置autoFlush,刷新缓冲区才有输出
                Scanner scanner = new Scanner(System.in);
                while(true){
                    String ClientMessage = scanner.nextLine();
                    pw.println(ClientMessage);
                    if(ClientMessage.equals("/logout")){
                        //客户端 优雅的退出
                        //socket.shutdownOutput();
                        break;
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                ClientClose();
            }
        }
    }


    private class GetMsgFromServer implements Runnable{
        @Override
        public void run(){
            try{
                InputStream in = socket.getInputStream();
                InputStreamReader isr = new InputStreamReader(in, StandardCharsets.UTF_8);
                BufferedReader br = new BufferedReader(isr);
                String msg;
                while ((msg=br.readLine())!=null){
                    if(msg.equals("/logout")) {
                        break;
                    }
                    else{
                        System.out.println(msg);
                    }
                }
            } catch (IOException e){
                e.printStackTrace();
            } finally {
                ClientClose();
            }
        }
    }
}
