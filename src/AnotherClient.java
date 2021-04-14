import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;

/**
 * @program: XJTU_chatroom
 * @description: only one thread
 * @author: 席卓然
 * @create: 2021-04-13 19:59
 * @version 1.0.0
 **/

public class AnotherClient {
    private Socket socket = null;
    private PrintWriter pw = null;

    /**
     * 客户端的动作：
     * sendMessage/readMessage/connectServer/
     * disconnect
     */

    public Socket connectServer(int port) {//连接服务器，并且返回建立连接后的socket
        try {
            System.out.println("connecting...");
            socket = new Socket("111.229.120.197",port);//在Client端，需要指定host的ip地址和端口
            System.out.println("connect successfully.");
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            return socket;
        }
    }

    public void sendMessage(int port) {
        try {
            socket = connectServer(port);
            while(true){
                pw = new PrintWriter(socket.getOutputStream(),true);//设置autoFlush,刷新缓冲区才有输出
                Scanner scanner = new Scanner(System.in);
                pw.println(scanner.nextLine());
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            pw.close();
        }
    }

    public static void main(String[] args) {
        ChatClient MyClient = new ChatClient();
        System.out.println("input the port:");
        MyClient.sendMessage(new Scanner(System.in).nextInt());
    }
}
