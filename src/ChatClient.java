import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.util.Date;

/**
 * @program: XJTU_chatroom
 * @description: GUI with Swing
 * @create: 2021-04-13 19:59
 * @version 2.0.0
 **/

public class ChatClient extends JFrame {
    private Socket socket = null;
    private volatile int count = 0;
    private final JTextField tf_send;
    private final SimpleAttributeSet attrset;
    private final JTextPane ta_show;
    /**
     * 客户端的动作：
     * sendMessage/readMessage/connectServer/
     * disconnect
     */

    public void ChatConnect(){
        try{
            System.out.println("connecting...");
            socket = new Socket("111.229.120.197",10170);//在Client端，需要指定host的ip地址和端口
            System.out.println("connect successfully.");
            start();
        } catch (IOException e){
            e.printStackTrace();
        }
    }

    //聊天主窗口
    public ChatClient(){

        super();
        //换个好看点的UI
        try {
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        }catch(Exception e) {
            e.printStackTrace();
        }

        //设置窗体的具体参数

        setTitle("XJTU_chatroom Client");

        setSize(800,600);

        final JPanel panel = new JPanel();
        getContentPane().add(panel,BorderLayout.SOUTH);

        final JLabel label = new JLabel();
        label.setText("请输入聊天内容：");
        panel.add(label);
        //System.out.println("debug");

        tf_send = new JTextField();
        tf_send.setPreferredSize(new Dimension(180,30));
        panel.add(tf_send);

        final JButton button = new JButton();
        button.addActionListener(e -> {
            //点击发送button，新建一个发送线程并加入到就绪队列
            if(socket != null)
            {
                SendMsgToServer sender1 = new SendMsgToServer();
                Thread thread = new Thread(sender1);
                thread.start();
            } else {
                JOptionPane.showMessageDialog(null,"请先登录！","提示",JOptionPane.ERROR_MESSAGE);
            }

        });
        button.setText("发送");
        panel.add(button);

        //将主窗口分为左右两部分
        final JSplitPane splitPane = new JSplitPane();
        splitPane.setDividerLocation(100);
        getContentPane().add(splitPane,BorderLayout.CENTER);

        //分割后的右部用来展示聊天框，设置为滚动
        final JScrollPane scrollPaneRight = new JScrollPane();
        splitPane.setRightComponent(scrollPaneRight);

        ta_show = new JTextPane();
        attrset = new SimpleAttributeSet();
        StyleConstants.setFontSize(attrset,14);
        StyleConstants.setFontFamily(attrset,"黑体");
        scrollPaneRight.setViewportView(ta_show);

        final JScrollPane scrollPaneLeft = new JScrollPane();
        splitPane.setLeftComponent(scrollPaneLeft);

        /*
          用户登录界面
         */
        final JPanel userPanel = new JPanel();
        getContentPane().add(userPanel,BorderLayout.NORTH);

        final JButton button_login = new JButton();
        //打开登录界面
        button_login.addActionListener(e -> {
            //登录时，传输用户名与密码到服务器端
            new loginDialog().createLoginDialog();
        });
        button_login.setText("登录");
        userPanel.add(button_login);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        //居中
        setLocationRelativeTo(null);
    }

    //登录界面的类
    public class loginDialog extends JDialog {
        private JTextField userIn = null;//用户名
        private JPasswordField keyIn = null;//密码
        private JDialog jDialog = null;
        public void createLoginDialog() {
            jDialog = new JDialog();

            jDialog.setLayout(null);

            jDialog.setBounds(300,400,400,200);
            jDialog.setTitle("登录界面");

            Container c = jDialog.getContentPane();
            JLabel userName = new JLabel();
            userName.setText("用户名：");
            userName.setBounds(25,50,50,30);
            c.add(userName);

            JLabel password = new JLabel();
            password.setText("密码：");
            password.setBounds(25,100,50,30);
            c.add(password);

            userIn = new JTextField();
            userIn.setBounds(100,50,100,30);
            c.add(userIn);
            keyIn = new JPasswordField();
            keyIn.setBounds(100,100,100,30);
            c.add(keyIn);

            JButton login = new JButton();
            login.setText("登录");
            login.setBounds(225 ,100,100,20);
            login.addActionListener(e -> {
                //传输username和password到服务器端
                ChatConnect();
                //这部分说明登录成功可以交给服务器端，这里只是用来测试
                Document document = ta_show.getDocument();
                try {
                    document.insertString(document.getLength() , userIn.getText() + " has logged in." +"\n", attrset);
                } catch (BadLocationException badLocationException) {
                    badLocationException.printStackTrace();
                }
                jDialog.dispose();
            });
            c.add(login);

            jDialog.setVisible(true);
        }

        public String getUsername(){
            return userIn != null ? userIn.getText() : null;
        }

        public String getPassword(){
            return keyIn != null ? keyIn.getText() : null;
        }
    }


    //开启监听信息的线程--长连接
    public void start(){
        try {
            GetMsgFromServer getter = new GetMsgFromServer();
            Thread tg = new Thread(getter);
            inc();
            tg.start();
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

    //主方法
    public static void main(String[] args) {
        EventQueue.invokeLater(() -> {
            ChatClient MyClientFrame = new ChatClient();
            MyClientFrame.setVisible(true);
        });
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

    //向服务器发送消息
    private class SendMsgToServer implements Runnable{
        @Override
        public void run(){
            try {
                //socket = connectServer(port);
                PrintWriter pw = new PrintWriter(socket.getOutputStream(), true);//设置autoFlush,刷新缓冲区才有输出
                //Scanner scanner = new Scanner(System.in);

                //改为从tf_send获得消息
                String ClientMessage = tf_send.getText();
                pw.println(ClientMessage);

                DateFormat dateFormat = DateFormat.getDateInstance();
                String date = dateFormat.format(new Date());
                dateFormat = DateFormat.getTimeInstance(DateFormat.MEDIUM);
                String timeString = dateFormat.format(new Date());
                String userName = "我";


                Document docs = ta_show.getDocument();
                try {
                    docs.insertString(docs.getLength(),userName+" "+date+" "+timeString+"： \n "+ ClientMessage +"\n ",attrset);
                } catch (BadLocationException e) {
                    e.printStackTrace();
                }
                //ta_show.setLineWrap(true);

                tf_send.setText(null);
                tf_send.requestFocus();
                //System.out.println(ClientMessage);
                if(ClientMessage.equals("/logout")){
                    //客户端 优雅的退出
                    ClientClose();
                    JOptionPane.showMessageDialog(null,"您已登出！","提示",JOptionPane.PLAIN_MESSAGE);

                }
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
    }

    //从服务器获得信息的线程
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
                        //将接收到的消息显示出来：(目前不考虑时间)
                        Document docs = ta_show.getDocument();
                        if(msg.contains("says")) {
                            //是消息内容
                            try {
                                docs.insertString(docs.getLength() , msg +"\n ", attrset);
                            } catch (BadLocationException e) {
                                e.printStackTrace();
                            }
                        } else {//是登录/登出消息
                            StyleConstants.setBold(attrset , true);
                            StyleConstants.setFontSize(attrset,10);
                            try {
                                docs.insertString(docs.getLength() , msg +"\n ", attrset);
                            } catch (BadLocationException e) {
                                e.printStackTrace();
                            } finally {
                                StyleConstants.setBold(attrset , false);
                                StyleConstants.setFontSize(attrset,14);
                            }
                        }
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
