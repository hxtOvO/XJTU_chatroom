import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
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
        } catch (Exception e){
            e.printStackTrace();
        }
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
                while(true){
                    Scanner scanner = new Scanner(System.in);
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
