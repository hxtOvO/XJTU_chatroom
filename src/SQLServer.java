import java.sql.*;
import java.util.Vector;

public class SQLServer {
    static final String JDBC_DRIVER = "com.mysql.cj.jdbc.Driver";
    static final String DB_URL = "jdbc:mysql://localhost:3306/Server";
    //用户名和密码
    static final String User = "kkuan";
    static final String Password = "enderii2";
    //构造函数,注册到JDBC
    public SQLServer(){
        try{
            Class.forName(JDBC_DRIVER);
        }catch (ClassNotFoundException ce){
            ce.printStackTrace();
        }
    }
    //登录验证方法,执行一个查询
    public Integer LogInAuth(String name,String pw){
        try(Connection conn=DriverManager.getConnection(DB_URL,User,Password)){
            try (PreparedStatement ps=conn.prepareStatement("SELECT password from Clients WHERE name = ?")){
                ps.setObject(1,name);
                try(ResultSet rs = ps.executeQuery()){
                    if(rs.next()){
                        if(pw.equals(rs.getString("password"))) return 1;
                        else return -1;
                    }
                    else return 0;
                }
            }
        }catch (SQLException se){
            se.printStackTrace();
        }
        return -2;
    }
    //添加一个新用户,并将其设置为在线
    public void AddClient(String name,String pw,Integer num){
        try(Connection conn=DriverManager.getConnection(DB_URL,User,Password)){
            try(PreparedStatement ps=conn.prepareStatement("INSERT INTO Clients (name, password, flag, num) VALUES (?,?,?,?)")){
                ps.setObject(1,name);
                ps.setObject(2,pw);
                ps.setObject(3,true);
                ps.setObject(4,num);
                ps.executeUpdate();
            }
        }catch (SQLException se){
            se.printStackTrace();
        }
    }
    //更新用户在线状态的方法
    public void ChangeOnline(String name, boolean flag, Integer num){
        try(Connection conn=DriverManager.getConnection(DB_URL,User,Password)){
            String sql = "UPDATE Clients " +
                    "SET flag = ?, num = ? " +
                    "WHERE name = ?";
            try (PreparedStatement ps=conn.prepareStatement(sql)){
                ps.setObject(1,flag);
                if(!flag){ ps.setObject(2,null); }
                else {ps.setObject(2,num);}
                ps.setObject(3,name);
                ps.executeUpdate();
            }
        }catch (SQLException se){
            se.printStackTrace();
        }
    }
    //获取在线状态的方法,包括群和用户
    public String GetOnlineStatus(String name){
        StringBuilder sb = new StringBuilder(1024);
        try(Connection conn=DriverManager.getConnection(DB_URL,User,Password)){
            String sql1 = "SELECT name " +
                    "FROM Clients " +
                    "WHERE flag = true";
            String sql2 = "SELECT name " +
                    "FROM Clients " +
                    "WHERE flag = false";
            String sql3 = "SELECT DISTINCT gName " +
                    "FROM TGroup " +
                    "WHERE cName = ?";
            try (Statement stmt=conn.createStatement()){
                try(ResultSet rs= stmt.executeQuery(sql1)){
                    while(rs.next()){
                        sb.append(rs.getString("name"))
                                .append("`");
                    }
                }
                try(PreparedStatement ps = conn.prepareStatement(sql3)){
                    ps.setObject(1,name);
                    try(ResultSet rs = ps.executeQuery()){
                        while(rs.next()){
                            sb.append("$");
                            sb.append(rs.getString("gName"))
                                    .append("`");
                        }
                    }
                }
                sb.append("@");
                try(ResultSet rs = stmt.executeQuery(sql2)){
                    while(rs.next()){
                        sb.append(rs.getString("name"))
                                .append("`");
                    }
                }

                String st = sb.toString();
                if(st.length()!=0) return st;
                else return "None";
            }
        }catch (SQLException se){se.printStackTrace();}
        return "Error";
    }
    //获取name对应的num,执行一个查询
    public Integer FindNum(String name){
        try(Connection conn=DriverManager.getConnection(DB_URL,User,Password)){
            try (PreparedStatement ps=conn.prepareStatement("SELECT flag, num from Clients WHERE name = ?")){
                ps.setObject(1,name);
                try(ResultSet rs = ps.executeQuery()){
                    if(rs.next() && rs.getBoolean("flag")){
                        return rs.getInt("num");
                    }
                    else return -1;
                }
            }
        }catch (SQLException se){
            se.printStackTrace();
        }
        return -2;
    }
    //关闭服务端时将所有客户端都设为离线
    public void ServerClose() {
        try(Connection conn=DriverManager.getConnection(DB_URL,User,Password)){
            String sql = "UPDATE Clients " +
                    "SET flag = false, num = null";
            try(Statement stmt=conn.createStatement()){
                stmt.executeUpdate(sql);
            }
        }catch (SQLException se){se.printStackTrace();}
    }
    //连接群和用户
    public void GroupConnect(String gName, String cName){
        try(Connection conn=DriverManager.getConnection(DB_URL,User,Password)){
            String sql = "INSERT INTO TGroup "+
                    "(gName, cName) VALUES" +
                    "(?, ?)";
            try(PreparedStatement ps= conn.prepareStatement(sql)){
                ps.setObject(1,gName);
                ps.setObject(2,cName);
                ps.executeUpdate();
            }
        }catch (SQLException se){
            se.printStackTrace();
        }
    }
    //查找群中的用户
    public Vector<Integer> GroupSearch(String gName){
        Vector<Integer> num = new Vector<>();
        try(Connection conn=DriverManager.getConnection(DB_URL,User,Password)){
            String sql = "SELECT c.num " +
                    "from TGroup as t " +
                    "join Clients as c " +
                    "on t.cName = c.name " +
                    "where t.gName = ? " +
                    "and c.num is not null";
            try(PreparedStatement ps=conn.prepareStatement(sql)){
                ps.setObject(1,gName);
                try(ResultSet rs= ps.executeQuery()){
                    while(rs.next()){
                        num.add(rs.getInt("num"));
                    }
                }
            }
        } catch (SQLException se){
            se.printStackTrace();
        }
        return num;
    }
}
