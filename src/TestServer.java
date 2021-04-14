import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TestServer {
    private ServerSocket server = null;
    private Socket soc = null;

    public TestServer()
    {
        try {
            server = new ServerSocket(10170,3);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Socket GetSoc()
    {
        try {
            System.out.println("Server established.\n");
            boolean flag = true;
            while(flag)
            {
                System.out.println("Waiting for connection...\n");
                soc = server.accept();
                if(soc!=null) flag=false;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return soc;
    }

    public static void main(String[] args)
    {
        TestServer MainServer = new TestServer();
        ExecutorService es = Executors.newFixedThreadPool(4);
        for (int i=0;i<2;i++){
            es.submit(new Handler(""+i,i, MainServer.GetSoc()));
        }
        es.shutdown();
    }
}

class Handler implements Runnable {
    private final String name;
    private final int num;
    private final Socket s;
    private BufferedReader br = null;
    public Handler(String name, int num, Socket s) {
        this.name = name;
        this.num=num;
        this.s=s;
    }

    public void GetMsg(Socket s, int i)
    {
        try {
            while(s.isConnected()) {
                br = new BufferedReader(new InputStreamReader(s.getInputStream()));
                String msg = br.readLine();
                System.out.println("Client" + i + " says: " + msg);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            br.close();
            s.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run()
    {
        System.out.println("Start task: " + name);
        try {
            GetMsg(s, num);
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("End Task: " + name);
    }
}
