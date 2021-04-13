import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Scanner;

/**
 * @program: XJTU_chatroom
 * @description:
 * @author: 席卓然
 * @create: 2021-4-13
 * @version 1.0.0
**/

public class ChatServer {
    private Socket socket = null;//初始化socket对象
    private BufferedReader br = null;//初始化br读缓存对象
    private ServerSocket server = null;
    /**
     * 注意Socket和ServerSocket的区别：
     * ServerSocket用于服务器端监听，只需要一个参数(port)
     * 通过先创建一个ServerSocket对象，再对这个对象调用accept()方法
     * accept会阻塞直到收到一个Client的请求，并返回一个与其对应的Socket
     * 对象。此后操作Socket对象即可。
     */

    public Socket getSocket(int port) {//返回port对应的监听Socket
        try {
            server = new ServerSocket(port);
            System.out.println("ServerSocket established.");
            while(true) {
                System.out.println("waiting for connection");
                socket = server.accept();//阻塞等待Client请求
                if(socket == null)continue;
                else break;
            }
        } catch (IOException e){
            e.printStackTrace();//将错误打印到控制台
        } finally {
            return socket;
        }
    }

    public void getClientMessage(int port) {//服务器端获得Client发出信息的方法
            try {
                Socket sck = getSocket(port);
                while(sck.isConnected()) {
                    //如果对应port的socket已经打开
                    br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    String message = br.readLine();//读取出message
                    System.out.println("Client says :"+message);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            try {
                br.close();
                socket.close();
                server.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
    }

    public void transformMessage(String message) {
        /**
         * 服务器端需要转发另一个Client的消息
         * 这里为测试需要，直接将message作为参数输入
         * message需要修改为从另一个Client.getClientMessage()得到的信息
        */
    }

    public static void main(String[] args) {
        System.out.println("input the port:");
        Scanner scanner = new Scanner(System.in);
        ChatServer MyChatServer = new ChatServer();
        MyChatServer.getClientMessage(scanner.nextInt());
    }

}
