import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.util.*;

/**
 * @version 5.0.0
 * @program: XJTU_chatroom
 * @description: 1.完全支持在线文件的断点续传
 *               2.完全支持离线文件的断点续传
 *               3.支持动态进度条显示文件传输进度（大文件）
 *               4.支持群聊，加入群聊和创建群聊
 *               5.发文件方法和收文件方法集成在对应的JFrame类里
 * @create: 2021-04-13 19:59
 **/

public class ChatClient extends JFrame {
    private Socket socket = null;
    private volatile int count = 0;
    private final JTextField tf_send;
    private final SimpleAttributeSet attrset;
    private final JTextPane ta_show;
    private final JList<String> user_list;
    private Vector<String> user_all;
    private int userIndex = -1;
    private String username;
    private HashMap<String, JTextPane> ChatWindowsMap;
    private Vector<String> vec_group;
    private Vector<String> vec_online;
    private Vector<String> vec_offline;
    private Color[] color;


    public void ChatConnect() {
        try {
            System.out.println("connecting...");
            socket = new Socket("111.229.120.197", 10170);//在Client端，需要指定host的ip地址和端口
            System.out.println("connect successfully.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void ClientClose() {
        try {
            if (count == 0 && socket != null) {
                socket.close();
                tf_send.setText(null);
                ta_show.setText("");
                user_all.clear();
                user_list.setListData(user_all);


                System.out.println("Client has disconnected...");
                socket = null;
            } else {
                dec();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //聊天主窗口
    public ChatClient() {

        super();
        //换个好看点的UI
        try {
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        //设置窗体的具体参数

        setTitle("XJTU_chatroom Client");

        setSize(800, 600);

        final JPanel panel = new JPanel();
        getContentPane().add(panel, BorderLayout.SOUTH);

        final JLabel label = new JLabel();
        label.setText("请输入聊天内容：");
        panel.add(label);
        //System.out.println("debug");

        tf_send = new JTextField();
        tf_send.setPreferredSize(new Dimension(180, 30));
        tf_send.addKeyListener(new KeyAdapter() {
            @Override
            public void keyTyped(KeyEvent e) {
                if (e.getKeyChar() == KeyEvent.VK_ENTER) {
                    if (socket != null) {
                        SendMsgToServer sender1 = new SendMsgToServer();
                        sender1.setClientMessage(tf_send.getText());
                        Thread thread = new Thread(sender1);
                        thread.start();
                    } else {
                        JOptionPane.showMessageDialog(null, "请先登录！", "提示", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }
        });

        panel.add(tf_send);

        final JButton button = new JButton();
        button.addActionListener(e -> {
            //点击发送button，新建一个发送线程并加入到就绪队列
            if (socket != null) {
                SendMsgToServer sender1 = new SendMsgToServer();
                sender1.setClientMessage(tf_send.getText());
                Thread thread = new Thread(sender1);
                thread.start();
            } else {
                JOptionPane.showMessageDialog(null, "请先登录！", "提示", JOptionPane.ERROR_MESSAGE);
            }
        });
        button.setText("发送");
        panel.add(button);

        //发送在线文件选项
        final JButton onlineFileButton = new JButton();
        onlineFileButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (socket != null) {
                    FileChooser fileChooser = new FileChooser();
                    fileChooser.createFileChooser();
                    File file_send = fileChooser.getFile();
                    //System.out.println(file_send.getName());
                    //选择好文件，弹出确认对话框
                    if (file_send != null) {
                        int confirm = JOptionPane.showConfirmDialog(null, "是否确认发送" + file_send.getName() + "?", "发送确认", JOptionPane.YES_NO_OPTION);
                        if (confirm == JOptionPane.YES_OPTION) {
                            //确认发送 发送信息到对方
                            try {
                                //向服务器写入该次文件传输的请求,并附加上文件的名字和期待建立连接的端口号
                                PrintWriter pw = new PrintWriter(socket.getOutputStream(), true);
                                OfflineFile sf = new OfflineFile(file_send,"111.229.120.197",10180);
                                Thread thread = new Thread(sf);
                                thread.start();
                                pw.println("@" + user_all.elementAt(user_list.getSelectedIndex()) + ":/OnlineFile:" + file_send.getName() + "/FileLength:" + sf.GetFileLength()+"/Port:10180");
                                System.out.println("@" + user_all.elementAt(user_list.getSelectedIndex()) + ":/OnlineFile:" + file_send.getName() + "/FileLength:" + sf.GetFileLength());
                                //System.out.println("发送ip是：" + InetAddress.getLocalHost().getHostAddress());
                            } catch (IOException ioException) {
                                ioException.printStackTrace();
                            }
                        }
                    }
                } else {
                    JOptionPane.showMessageDialog(null, "请先登录！", "提示", JOptionPane.ERROR_MESSAGE);
                }
            }
        });
        onlineFileButton.setText("发送在线文件");
        panel.add(onlineFileButton);

        //发送离线文件选项
        final JButton offlineFileButton = new JButton();
        offlineFileButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (socket != null) {
                    FileChooser fileChooser = new FileChooser();
                    fileChooser.createFileChooser();
                    File file_send = fileChooser.getFile();
                    //System.out.println(file_send.getName());
                    //选择好文件，弹出确认对话框
                    if (file_send != null) {
                        int confirm = JOptionPane.showConfirmDialog(null, "是否确认发送" + file_send.getName() + "?", "发送确认", JOptionPane.YES_NO_OPTION);
                        if (confirm == JOptionPane.YES_OPTION) {
                            //确认发送 发送信息到对方
                            try {
                                //向服务器写入该次文件传输的请求,并附加上文件的名字和期待建立连接的端口号
                                PrintWriter pw = new PrintWriter(socket.getOutputStream(), true);
                                OfflineFile sf = new OfflineFile(file_send, "111.229.120.197", 10180);
                                Thread thread = new Thread(sf);
                                thread.start();
                                pw.println("@" + user_all.elementAt(user_list.getSelectedIndex()) + ":/OfflineFile:" + file_send.getName() + "/FileLength:" + sf.GetFileLength());
                                System.out.println("@" + user_all.elementAt(user_list.getSelectedIndex()) + ":/OnlineFile:" + file_send.getName() + "/FileLength:" + sf.GetFileLength());
                            } catch (IOException ioException) {
                                ioException.printStackTrace();
                            }
                        }
                    }
                } else {
                    JOptionPane.showMessageDialog(null, "请先登录！", "提示", JOptionPane.ERROR_MESSAGE);
                }
            }
        });
        offlineFileButton.setText("发送离线文件");
        panel.add(offlineFileButton);

        //接收服务器上的离线文件
        final JButton getOfflineFileButton = new JButton("接收离线文件");
        getOfflineFileButton.addActionListener(e -> new getOfflineDialog().createDialog());
        panel.add(getOfflineFileButton);

        //将主窗口分为左右两部分
        final JSplitPane splitPane = new JSplitPane();
        splitPane.setDividerLocation(150);
        getContentPane().add(splitPane, BorderLayout.CENTER);

        //分割后的右部用来展示聊天框，设置为滚动
        final JScrollPane scrollPaneRight = new JScrollPane();
        splitPane.setRightComponent(scrollPaneRight);

        ta_show = new JTextPane();
        attrset = new SimpleAttributeSet();
        StyleConstants.setFontSize(attrset, 14);
        StyleConstants.setFontFamily(attrset, "黑体");
        scrollPaneRight.setViewportView(ta_show);

        final JScrollPane scrollPaneLeft = new JScrollPane();
        splitPane.setLeftComponent(scrollPaneLeft);

        //获得在线名单
        user_all = new Vector<>(10);

        user_list = new JList<>();
        user_list.setListData(user_all);
        user_list.setBorder(BorderFactory.createTitledBorder("注册用户名单"));
        user_list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                super.mouseClicked(e);
                userIndex = user_list.getSelectedIndex();//获得当前选择的下标
                //在name-JTextPane的键对中找到选定name对应的JTextPane,将RightPane赋值给他
                //其实这里相当于构建Document了，把对应的Document放进ta_show
                if(color[userIndex] == Color.cyan){
                    ta_show.setDocument(ChatWindowsMap.get("$"+user_all.elementAt(userIndex)).getDocument());
                } else {
                    ta_show.setDocument(ChatWindowsMap.get(user_all.elementAt(userIndex)).getDocument());
                }
            }
        });

        scrollPaneLeft.setViewportView(user_list);


        /*
          用户登录界面
         */
        final JPanel userPanel = new JPanel();
        getContentPane().add(userPanel, BorderLayout.NORTH);

        final JButton button_login = new JButton();
        //打开登录界面
        button_login.addActionListener(e -> {
            //登录时，传输用户名与密码到服务器端
            new loginDialog().createLoginDialog();
        });
        button_login.setText("登录");
        userPanel.add(button_login);

        final JButton button_logout = new JButton();
        button_logout.addActionListener(e -> {
            SendMsgToServer sender1 = new SendMsgToServer();
            sender1.setClientMessage("/logout");
            Thread thread = new Thread(sender1);
            thread.start();
        });
        button_logout.setText("退出");
        userPanel.add(button_logout);

        final JButton newGroup = new JButton("创建群聊");
        newGroup.addActionListener(e -> {
            //向服务端发消息建立一个名为name的群聊
            if (socket != null) {
                JDialog jDialog = new JDialog();
                JTextField fileName;

                jDialog.setLayout(null);

                jDialog.setBounds(300, 400, 400, 200);
                jDialog.setTitle("创建群聊");

                Container c = jDialog.getContentPane();
                JLabel userName = new JLabel();
                userName.setText("群聊名：");
                userName.setBounds(25, 50, 50, 30);
                c.add(userName);

                fileName = new JTextField();
                fileName.setBounds(100, 50, 100, 30);
                fileName.addKeyListener(new KeyAdapter() {
                    @Override
                    public void keyTyped(KeyEvent e) {
                        if (e.getKeyChar() == KeyEvent.VK_ENTER) {
                            try {
                                PrintWriter pw = new PrintWriter(socket.getOutputStream(),true);
                                pw.println("");//通知服务器建立群聊
                            } catch (IOException ioException) {
                                ioException.printStackTrace();
                            }
                        }
                    }
                });
                c.add(fileName);

                JButton get = new JButton("确定");
                get.setBounds(225, 100, 100, 20);
                get.addActionListener(e1 -> {
                    //PrintWriter pw = null;
                    try {
                        PrintWriter pw = new PrintWriter(socket.getOutputStream(),true);
                        pw.println("@server:/GroupCreate:"+fileName.getText());//写入一个请求加入的信息给服务器
                        jDialog.dispose();
                    } catch (IOException ioException) {
                        ioException.printStackTrace();
                    }

                });
                c.add(get);
                jDialog.setVisible(true);
            } else {
                JOptionPane.showMessageDialog(null, "请先登录！", "提示", JOptionPane.ERROR_MESSAGE);
            }
        });
        userPanel.add(newGroup);

        final JButton joinGroup = new JButton("加入群聊");
        joinGroup.addActionListener(e -> {
            //加入一个名为name的群聊
            if (socket != null) {
                JDialog jDialog = new JDialog();
                JTextField fileName;

                jDialog.setLayout(null);

                jDialog.setBounds(300, 400, 400, 200);
                jDialog.setTitle("加入群聊");

                Container c = jDialog.getContentPane();
                JLabel userName = new JLabel();
                userName.setText("群聊名：");
                userName.setBounds(25, 50, 50, 30);
                c.add(userName);

                fileName = new JTextField();
                fileName.setBounds(100, 50, 100, 30);
                fileName.addKeyListener(new KeyAdapter() {
                    @Override
                    public void keyTyped(KeyEvent e) {
                        if (e.getKeyChar() == KeyEvent.VK_ENTER) {
                            try {
                                PrintWriter pw = new PrintWriter(socket.getOutputStream(),true);
                                pw.println("");//通知服务器建立群聊
                            } catch (IOException ioException) {
                                ioException.printStackTrace();
                            }
                        }
                    }
                });
                c.add(fileName);

                JButton get = new JButton("确定");
                get.setBounds(225, 100, 100, 20);
                get.addActionListener(e12 -> {
                    //PrintWriter pw = null;
                    try {
                        PrintWriter pw = new PrintWriter(socket.getOutputStream(),true);
                        pw.println("@server:/GroupJoin:"+fileName.getText());//写入一个请求加入的信息给服务器
                        jDialog.dispose();
                    } catch (IOException ioException) {
                        ioException.printStackTrace();
                    }

                });
                c.add(get);
                jDialog.setVisible(true);
            } else {
                JOptionPane.showMessageDialog(null, "请先登录！", "提示", JOptionPane.ERROR_MESSAGE);
            }

        });
        userPanel.add(joinGroup);

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        //居中
        setLocationRelativeTo(null);
    }

    //开启监听信息的线程--长连接
    public void start() {
        try {
            GetMsgFromServer getter = new GetMsgFromServer();
            Thread tg = new Thread(getter);
            inc();
            tg.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //修改为获得两份名单 -> Vec_online Vec_offline
    public Vector<String> getOnlineList() {
        String str = null;
        try {
            PrintWriter pw = new PrintWriter(socket.getOutputStream(), true);
            pw.println("request_for_onlineUser");//向服务器发请求
            System.out.println("getting online list..");
            InputStreamReader isr = new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8);
            BufferedReader br = new BufferedReader(isr);
            //String str = null;
            //读返回的在线名单
            while (socket.isConnected()) {
                str = br.readLine();
                if (str != null) break;//阻塞循环直到读取到在线名单
            }
            System.out.println("Get");
            System.out.println(str);

        } catch (IOException e) {
            e.printStackTrace();
        }
        assert str != null;
        String[] ss = str.split("@");

        vec_online = new Vector<>(Arrays.asList(ss[0].split("`")));
        vec_offline = new Vector<>(Arrays.asList(ss[1].split("`")));
        //vec_group = new Vector<>(Arrays.asList(ss[2].split("`")));
        //ss[0]处理后是当前在线的，ss[1]处理后是当前离线的,ss[2]是当前该用户加入的群聊
        return update_user();
    }

    public void removeGroup(){
        vec_group = new Vector<>();
        for(int i=0;i<user_all.size();i++){
            if(user_all.elementAt(i).startsWith("$")){
                vec_group.add(user_all.elementAt(i));
                user_all.set(i,user_all.elementAt(i).substring(1));
            }
        }
    }

    //更新用户名单
    public Vector<String> update_user() {
        Vector<String> tmp = new Vector<>(vec_online);
        tmp.add(0,"server");
        for (int i = 0; i < vec_offline.size(); i++) {
            tmp.add(vec_offline.elementAt(i));
        }
        return tmp;
    }

    //更新color
    public void update_color() {
        color = new Color[user_all.size()];
        for (int i = 0; i < user_all.size(); i++) {
            if (vec_online.contains(user_all.elementAt(i))) {
                color[i] = user_all.elementAt(i).startsWith("$") ? Color.cyan : Color.yellow;
            } else if(vec_offline.contains(user_all.elementAt(i))){
                color[i] = Color.gray;
            }  else {
                color[i] = Color.PINK;
                //System.out.println("server pink");
            }
            //System.out.println("i ," + color[i]);
        }
    }

    public synchronized void inc() {
        count++;
    }

    public synchronized void dec() {
        count--;
    }

    //主方法
    public static void main(String[] args) {
        EventQueue.invokeLater(() -> {
            ChatClient MyClientFrame = new ChatClient();
            MyClientFrame.setVisible(true);
        });
    }

    //接收离线文件界面类
    public class getOfflineDialog extends JDialog {
        private JTextField fileName;

        public void createDialog(){
            //private JTextField fileLength;
            JDialog jDialog = new JDialog();

            jDialog.setLayout(null);

            jDialog.setBounds(300, 400, 400, 200);
            jDialog.setTitle("选择接收文件");

            Container c = jDialog.getContentPane();
            JLabel userName = new JLabel();
            userName.setText("文件名：");
            userName.setBounds(25, 50, 50, 30);
            c.add(userName);

            fileName = new JTextField();
            fileName.setBounds(100, 50, 100, 30);
            fileName.addKeyListener(new KeyAdapter() {
                @Override
                public void keyTyped(KeyEvent e) {
                    if (e.getKeyChar() == KeyEvent.VK_ENTER) {
                        getConnect();
                    }
                }
            });
            c.add(fileName);

            JButton get = new JButton("确定");
            get.setBounds(225, 100, 100, 20);
            get.addActionListener(e -> getConnect());
            c.add(get);
            jDialog.setVisible(true);
        }

        public void getConnect(){

            try {
                PrintWriter pw = new PrintWriter(socket.getOutputStream(),true);
                pw.println("@server:/Download/"+fileName.getText());
            } catch (IOException e) {
                e.printStackTrace();
            }
            //System.out.println("@server:/Download/"+fileName.getText());

            new GetFileFrame("111.229.120.197",10180);
        }
    }

    //发送文件界面类
    public static class SendFileFrame extends JFrame {
        private static JFrame sendFileFrame;
        private static JProgressBar progressBar;
        private static JLabel label;
        private static int barSize;
        private static int barOffset;
        private static JButton stop;
        private static JButton goon;
        private static boolean resume = false;

        public SendFileFrame(File f, Socket socket) {
            sendFileFrame = new JFrame("文件发送");
            sendFileFrame.setSize(380, 120);
            Container container = sendFileFrame.getContentPane();
            container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));

            SendFile sf = new SendFile(f, socket);
            final Thread[] threadSf = {new Thread(sf)};
            threadSf[0].start();

            progressBar = new JProgressBar();
            label = new JLabel("正在发送：" + f.getName());
            container.add(label);
            progressBar.setOrientation(JProgressBar.HORIZONTAL);
            progressBar.setMinimum(0);
            progressBar.setMaximum(barSize);
            progressBar.setValue(barOffset);
            progressBar.setStringPainted(true);
            progressBar.setPreferredSize(new Dimension(150, 20));
            progressBar.setBorderPainted(true);
            progressBar.setBackground(Color.cyan);

            JButton cancel = new JButton("取消发送");
            cancel.addActionListener(e -> {
                JOptionPane.showMessageDialog(sendFileFrame, "已取消文件传输", "提示", JOptionPane.INFORMATION_MESSAGE);
                try {
                    socket.close();
                    sendFileFrame.dispose();
                } catch (IOException ioException) {
                    ioException.printStackTrace();
                }
            });
            JPanel proPanel = new JPanel();
            proPanel.setLayout(new FlowLayout(FlowLayout.LEFT));
            proPanel.add(progressBar);
            proPanel.add(cancel);

            stop = new JButton("暂停");
            stop.addActionListener(e -> {
                //resume = false;
                threadSf[0].stop();
                SwingUtilities.invokeLater(() -> {
                    stop.setEnabled(false);
                    goon.setEnabled(true);
                });
            });
            proPanel.add(stop);


            goon = new JButton("继续");
            goon.addActionListener(e -> {
                resume = true;
                threadSf[0] = new Thread(new SendFile(f, socket));
                threadSf[0].start();
                SwingUtilities.invokeLater(() -> {
                    goon.setEnabled(false);
                    stop.setEnabled(true);
                });
            });
            goon.setEnabled(false);
            proPanel.add(goon);


            container.add(proPanel);
            sendFileFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            sendFileFrame.setVisible(true);
        }

        //发送文件方法
        public static class SendFile implements Runnable {
            private final File file;
            private final Socket soc;
            private long size;

            public SendFile(File f, Socket socket) {
                file = f;
                soc = socket;
            }

            @Override
            public void run() {
                //为了支持随机存取把fis换为rad
                //FileInputStream fis = new FileInputStream(file);
                try {
                    DataInputStream dis = new DataInputStream(soc.getInputStream());
                    DataOutputStream dos = new DataOutputStream(soc.getOutputStream());
                    RandomAccessFile rad = new RandomAccessFile(file, "r");
                    byte[] bytes = new byte[1024];

                    if(resume){
                        //如果继续，直接继续进循环

                        //传输进度可视化
                        int length;
                        if (size < rad.length()) {
                            rad.seek(size);
                            while ((length = rad.read(bytes)) > 0) {
                                dos.write(bytes, 0, length);
                                SwingUtilities.invokeLater(() -> progressBar.setValue(++barOffset));
                                dos.flush();
                            }
                        }
                        System.out.println("Send over.");
                    }

                    dos.writeUTF(file.getName());
                    dos.flush();

                    String rsp = dis.readUTF();//对方已经接收的
                    if (rsp.equals("ok")) {
                        size = dis.readLong();
                        dos.writeLong(rad.length());
                        dos.writeUTF("ok");
                        dos.flush();

                        barSize = (int) (rad.length() / 1024);
                        barOffset = (int) (size / 1024);
                        SwingUtilities.invokeLater(() -> {
                            progressBar.setMaximum(barSize);
                            progressBar.setValue(barOffset);

                        });

                        //传输进度可视化
                        int length;
                        if (size < rad.length()) {
                            rad.seek(size);
                            while ((length = rad.read(bytes)) > 0) {
                                dos.write(bytes, 0, length);
                                SwingUtilities.invokeLater(() -> progressBar.setValue(++barOffset));
                                dos.flush();
                            }
                        }
                        System.out.println("Send over.");
                    }
                    JOptionPane.showMessageDialog(null, file.getName() + "已成功发送");
                    sendFileFrame.dispose();
                    soc.close();
                } catch (IOException e) {
                    System.out.println("发送中断.");
                    SwingUtilities.invokeLater(() -> label.setText("发送已中断，连接关闭"));
                    e.printStackTrace();
                }

            }
        }
    }

    //登录界面的类
    public class loginDialog extends JDialog {
        private JTextField userIn = null;//用户名
        private JPasswordField keyIn = null;//密码
        private JDialog jDialog = null;

        public void createLoginDialog() {
            jDialog = new JDialog();

            jDialog.setLayout(null);

            jDialog.setBounds(300, 400, 400, 200);
            jDialog.setTitle("登录界面");

            Container c = jDialog.getContentPane();
            JLabel userName = new JLabel();
            userName.setText("用户名：");
            userName.setBounds(25, 50, 50, 30);
            c.add(userName);

            JLabel password = new JLabel();
            password.setText("密码：");
            password.setBounds(25, 100, 50, 30);
            c.add(password);

            userIn = new JTextField();
            userIn.setBounds(100, 50, 100, 30);
            userIn.addKeyListener(new KeyAdapter() {
                @Override
                public void keyTyped(KeyEvent e) {
                    if (e.getKeyChar() == KeyEvent.VK_ENTER) {
                        keyIn.requestFocus();
                    }
                }
            });
            c.add(userIn);
            keyIn = new JPasswordField();
            keyIn.setBounds(100, 100, 100, 30);
            keyIn.addKeyListener(new KeyAdapter() {
                @Override
                public void keyTyped(KeyEvent e) {
                    if (e.getKeyChar() == KeyEvent.VK_ENTER) {
                        executeLogin();
                    }
                }
            });
            c.add(keyIn);

            JButton login = new JButton();
            login.setText("登录");
            login.setBounds(225, 100, 100, 20);
            login.addActionListener(e -> executeLogin());
            c.add(login);
            jDialog.setVisible(true);
        }

        public void executeLogin() {
            ChatConnect();
            Integer logAuth = LogIn();
            //检测是否登录成功
            if (logAuth == 1) {
                //登录成功，获得当前在线名单
                user_all = getOnlineList();//这时user_all里面的还有$
                ChatWindowsMap = new HashMap<>();
                for (String ss : user_all) {
                    ChatWindowsMap.put(ss, new JTextPane());
                }

                update_color();


                removeGroup();
                user_list.setListData(user_all);
                user_list.setCellRenderer(new MyRenderer(color));
                //获得在线名单后，建立name与JTextPane的HashMap


                user_list.updateUI();
                username = userIn.getText();
                Document document = ta_show.getDocument();
                try {
                    document.insertString(document.getLength(), userIn.getText() + " has logged in." + "\n", attrset);
                } catch (BadLocationException badLocationException) {
                    badLocationException.printStackTrace();
                }
                start();
                jDialog.dispose();
            } else {
                if (logAuth == 0) {
                    JOptionPane.showMessageDialog(jDialog, "密码错误！", "提示", JOptionPane.ERROR_MESSAGE);
                } else if (logAuth == -1) {
                    JOptionPane.showMessageDialog(jDialog, "未知错误！", "提示", JOptionPane.ERROR_MESSAGE);
                }
                ClientClose();
            }
        }

        public Integer LogIn() {
            String name = getUsername();
            String pwd = getPassword();
            try {
                PrintWriter pw = new PrintWriter(socket.getOutputStream(), true);
                pw.println(name + "`" + pwd);
                System.out.println("logging in ...");
                InputStreamReader isr = new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8);
                BufferedReader br = new BufferedReader(isr);
                String str;
                while (true) {
                    str = br.readLine();
                    //System.out.println("reading");
                    if (str != null) break;
                }
                System.out.println("Get");
                if (Objects.equals(str, "1")) return 1;
                else if (Objects.equals(str, "0")) return 0;
                else return -1;
            } catch (IOException e) {
                e.printStackTrace();
            }
            return -1;
        }

        public String getUsername() {
            return userIn != null ? userIn.getText() : null;
        }

        public String getPassword() {
            return keyIn != null ? String.valueOf(keyIn.getPassword()) : null;
        }
    }

    //设置JList对象的类--用于修改颜色
    public static class MyRenderer extends DefaultListCellRenderer {
        private final Color[] rowColor;

        public MyRenderer(Color[] color) {
            //this.rows = rows;
            this.rowColor = color;
        }

        public Component getListCellRendererComponent(JList list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            for (int i = 0; i < rowColor.length; i++) {
                if (index == i) {
                    setBackground(rowColor[i]);
                }
            }
            return this;
        }
    }

    //选择发送文件的类
    public static class FileChooser extends JFileChooser {
        private File file = null;

        public void createFileChooser() {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setPreferredSize(new Dimension(800, 500));
            fileChooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
            fileChooser.showDialog(new JLabel(), "选择发送文件");
            file = fileChooser.getSelectedFile();
            if (file != null) {
                if (file.isDirectory()) {
                    //FilePath = file.getPath();
                    System.out.println("文件夹：" + file.getAbsolutePath());
                } else if (file.isFile()) {
                    //Filename = file.getAbsolutePath();
                    System.out.println("文件：" + file.getAbsolutePath());
                }
                //file = jFileChooser.getSelectedFile();
                System.out.println(fileChooser.getSelectedFile().getName());
            }
        }

        public File getFile() {
            return file;
        }
    }

    //向服务器发送消息
    private class SendMsgToServer implements Runnable {
        private String ClientMessage;

        public void setClientMessage(String clientMessage) {
            this.ClientMessage = clientMessage;
        }

        @Override
        public void run() {
            try {
                //socket = connectServer(port);
                PrintWriter pw = new PrintWriter(socket.getOutputStream(), true);//设置autoFlush,刷新缓冲区才有输出
                //Scanner scanner = new Scanner(System.in);

                //改为从tf_send获得消息
                //ClientMessage = tf_send.getText();

                //登出的消息不加头部
                if (ClientMessage.equals("/logout")) {
                    //客户端 优雅的退出
                    pw.println(ClientMessage);
                    ClientClose();
                    JOptionPane.showMessageDialog(null, "您已登出！", "提示", JOptionPane.PLAIN_MESSAGE);

                } else {
                    //常规消息，格式为：@name:ClientMessage
                    if(color[userIndex] == Color.yellow){
                        pw.println("@" + user_all.elementAt(userIndex) + ":" + ClientMessage);
                    } else if(color[userIndex] == Color.cyan){
                        pw.println("@$" + user_all.elementAt(userIndex)+":"+ClientMessage);
                    }

                    DateFormat dateFormat = DateFormat.getDateInstance();
                    String date = dateFormat.format(new Date());
                    dateFormat = DateFormat.getTimeInstance(DateFormat.MEDIUM);
                    String timeString = dateFormat.format(new Date());
                    //String userName = "我";

                    Document docs = ta_show.getDocument();
                    try {
                        docs.insertString(docs.getLength(), username + " " + date + " " + timeString + " to " + user_all.elementAt(userIndex) + "： \n" + ClientMessage + "\n", attrset);
                    } catch (BadLocationException e) {
                        e.printStackTrace();
                    }
                    tf_send.setText(null);
                    tf_send.requestFocus();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    //从服务器获得信息的线程
    private class GetMsgFromServer implements Runnable {
        @Override
        public void run() {
            try {
                InputStream in = socket.getInputStream();
                InputStreamReader isr = new InputStreamReader(in, StandardCharsets.UTF_8);
                BufferedReader br = new BufferedReader(isr);
                String msg;
                while ((msg = br.readLine()) != null) {
                    if (msg.equals("/logout")) {
                        break;
                    } else {
                        System.out.println(msg);
                        //将接收到的消息显示出来：(目前不考虑时间)
                        //Document docs = ta_show.getDocument();
                        if (msg.startsWith("@")) {
                            //以@开头说明是私聊消息

                            String[] ss = msg.substring(msg.indexOf(":") + 1).split("/");
                            //ss[0]:""
                            //ss[1]:SendFile:Filename
                            //ss[2]:FileLength:length
                            //ss[3]:Port:xxx
                            //ss[4]:IP:xxx.xxx.xxx.xxx
                            //System.out.println(ss[1] + ss[2]+ss[3]+ss[4]);

                            if (ss.length >= 2 && ss[1].startsWith("OnlineFile")) {
                                //如果是传在线文件请求
                                int confirm = JOptionPane.showConfirmDialog(null, "是否确认接收来自" + msg.substring(1, msg.indexOf(":")) + "的文件：\n" + ss[1].substring(ss[1].indexOf(":") + 1), "接收确认", JOptionPane.YES_NO_OPTION);
                                if (confirm == JOptionPane.YES_OPTION) {
                                    //new GetFile(ss[4].substring(ss[4].indexOf(":")+1), Integer.parseInt(ss[3].substring(ss[3].indexOf(":")+1)), Long.parseLong(ss[2].substring(ss[2].indexOf(":")+1)));
                                    new GetFileFrame(ss[4].substring(ss[4].indexOf(":")+1),Integer.parseInt(ss[3].substring(ss[3].indexOf(":") + 1)));
                                    //System.out.println("我收到的ip是 " +ss[4].substring(ss[4].indexOf(":")+1));
                                } else {
                                    Document docs = ChatWindowsMap.get(msg.substring(1, msg.indexOf(":"))).getDocument();
                                    try {
                                        docs.insertString(docs.getLength(), "拒绝了对方的文件：" + ss[1].substring(ss[1].indexOf(":") + 1), attrset);
                                    } catch (BadLocationException e) {
                                        e.printStackTrace();
                                    }
                                }

                            } else {
                                String str = msg.substring(1);
                                //String[] mss = str.split(":");

                                String fore = str.substring(0,str.indexOf(":"));
                                String later = str.substring(str.indexOf(":")+1);

                                //是消息内容 @name:msg
                                try {
                                    System.out.println(fore + " says " + later);
                                    Document docs = ChatWindowsMap.get(fore).getDocument();
                                    docs.insertString(docs.getLength(), fore + " says: " + later + "\n", attrset);
                                    //ta_show.setDocument(docs);
                                } catch (BadLocationException e) {
                                    e.printStackTrace();
                                }
                            }
                        } else if(msg.contains("log in.")||msg.contains("log out.")){
                            StyleConstants.setBold(attrset, true);
                            StyleConstants.setFontSize(attrset, 10);
                            try {
                                String[] ss = msg.split("\\s+");
                                if (ss[2].equals("in.")) {
                                    if(ss[0].startsWith("$")){
                                        //$name log in.
                                        ChatWindowsMap.put(ss[0],new JTextPane());
                                        System.out.println("adding " + ss[0]);
                                        vec_group.add(ss[0]);
                                        vec_online.add(ss[0]);
                                    } else {
                                        System.out.println("adding " + ss[0]);
                                        vec_online.add(ss[0]);
                                        vec_offline.remove(ss[0]);
                                        ChatWindowsMap.put(ss[0], new JTextPane());
                                    }
                                } else if (ss[2].equals("out.")) {
                                    vec_online.remove(ss[0]);
                                    vec_offline.add(ss[0]);
                                    ChatWindowsMap.remove(ss[0]);
                                }
                                //刷新user_online列表
                                user_all = update_user();
                                update_color();
                                removeGroup();
                                user_list.setListData(user_all);
                                user_list.setCellRenderer(new MyRenderer(color));
                                //user_list.updateUI();
                            } finally {
                                StyleConstants.setBold(attrset, false);
                                StyleConstants.setFontSize(attrset, 14);
                            }
                        } else if(msg.startsWith("$")) {
                            //群聊消息
                            //$groupname@sender:message
                            String[] ss = msg.split("@");
                            String fore = ss[0].substring(1);//groupName
                            String later = ss[1].substring(ss[1].indexOf(":")+1);//message
                            String mid = ss[1].substring(0,ss[1].indexOf(":"));
                            System.out.println(mid + " says " + later +" in " + fore);
                            Document docs = ChatWindowsMap.get(ss[0]).getDocument();
                            try {
                                docs.insertString(docs.getLength(),mid + " says "+later+"\n",attrset);
                            } catch (BadLocationException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                ClientClose();
            }
        }
    }

    //发送离线文件
    private static class OfflineFile implements Runnable {
        private Socket offSoc = null;
        private File file;

        @Override
        public void run() {
            new SendFileFrame(file, offSoc);
            //SendFileFrame.SendFile(file, offSoc);
        }

        public OfflineFile(File file, String ip, int port) {
            try {
                offSoc = new Socket(ip, port);
                System.out.println("connect");
                this.file = file;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public long GetFileLength() {
            return file.length();
        }
    }

    //文件接受界面类
    public static class GetFileFrame extends JFrame{
        private static JFrame getFileFrame;
        private static JProgressBar progressBar;
        private static JLabel label;
        private static int barSize;
        private static int barOffset;
        private static GetFile sf;

        public GetFileFrame(String ip,int p){
            getFileFrame = new JFrame("文件接收");
            getFileFrame.setSize(380, 120);
            getFileFrame.setLocationRelativeTo(null);
            Container container = getFileFrame.getContentPane();
            container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));

            sf = new GetFile(ip,p);
            Thread threadGf = new Thread(sf);
            threadGf.start();


            progressBar = new JProgressBar();
            label = new JLabel("正在接收...");
            container.add(label);
            progressBar.setOrientation(JProgressBar.HORIZONTAL);
            progressBar.setMinimum(0);
            progressBar.setMaximum(barSize);
            progressBar.setValue(barOffset);
            progressBar.setStringPainted(true);
            progressBar.setPreferredSize(new Dimension(150, 20));
            progressBar.setBorderPainted(true);
            progressBar.setBackground(Color.cyan);

            JButton cancel = new JButton("取消接收");
            cancel.addActionListener(e -> {
                JOptionPane.showMessageDialog(getFileFrame, "已取消文件接收", "提示", JOptionPane.INFORMATION_MESSAGE);
                try {
                    sf.soc.close();
                    getFileFrame.dispose();
                } catch (IOException ioException) {
                    ioException.printStackTrace();
                }
            });
            JPanel proPanel = new JPanel();
            proPanel.setLayout(new FlowLayout(FlowLayout.LEFT));
            proPanel.add(progressBar);
            proPanel.add(cancel);

            container.add(proPanel);
            getFileFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            getFileFrame.setVisible(true);
        }

        public static class GetFile implements Runnable{
            private Socket soc;
            String path = "D:\\XJTUchatroom\\";
            String fn = null;
            @Override
            public void run() {
                try {
                    if (soc.isConnected()) {
                        DataInputStream dis = new DataInputStream(soc.getInputStream());
                        DataOutputStream dos = new DataOutputStream(soc.getOutputStream());
                        String fileName = dis.readUTF();
                        fn = fileName;
                        dos.writeUTF("ok");
                        dos.flush();
                        Path p = Paths.get(path + fileName + ".temp");
                        File file = new File("D:\\XJTUchatroom" + File.separatorChar + fileName + ".temp");
                        if(!Files.exists(p)){ Files.createFile(p); }
                        RandomAccessFile rad = new RandomAccessFile(file, "rw");
                        long recSize = 0;
                        if (Files.exists(p) && Files.isRegularFile(p)) {
                            recSize = Files.size(p);
                        }
                        dos.writeLong(recSize);
                        dos.flush();
                        long allSize = dis.readLong();
                        String rsp = dis.readUTF();

                        barSize = (int) (allSize / 1024);
                        barOffset = (int) (recSize / 1024);

                        SwingUtilities.invokeLater(() -> {
                            progressBar.setMaximum(barSize);
                            progressBar.setValue(barOffset);
                        });

                        if (rsp.equals("ok")) {
                            rad.seek(recSize);
                            //seekableByteChannel.position(recSize);
                            int length;
                            byte[] bytes = new byte[1024];
                            //ByteBuffer buffer = ByteBuffer.allocate(1024);
                            while ((length = dis.read(bytes,0, bytes.length)) >0) {
                                rad.write(bytes, 0, length);
                                barOffset += length/1024;
                                SwingUtilities.invokeLater(() -> progressBar.setValue(barOffset));
                            }
                            System.out.println("Get over.");
                        }
                        JOptionPane.showMessageDialog(null, "文件已保存至：" + path + fileName);
                        getFileFrame.dispose();
                        soc.close();
                        rad.close();
                        Files.move(Paths.get(path + fn + ".temp"), Paths.get(path + fn));
                        //System.out.println(fileName);
                    }
                } catch (IOException e) {
                    System.out.println("接收中断");
                    SwingUtilities.invokeLater(() -> label.setText("接收已中断，连接关闭"));
                    JOptionPane.showMessageDialog(null,"接收已中断！");
                    e.printStackTrace();
                }
            }

            public GetFile(String ip, int port) {
                try {
                    System.out.println(ip + "," + port);
                    InetAddress ipaddr = InetAddress.getByName(ip);
                    System.out.println("建立连接用的ip：" + ipaddr);
                    soc = new Socket(ipaddr, port);
                    System.out.println(soc.isConnected());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

}
