import java.sql.*;

public class SQLServer {
    static final String JDBC_DRIVER = "com.mysql.cj.jdbc.Driver";
    static final String DB_URL = "jdbc:mysql://localhost:3306/Server";

    //用户名和密码
    static final String User = "kkuan";
    static final String Password = "enderii2";

    public SQLServer(){
        try{
            Class.forName(JDBC_DRIVER);
        }catch (ClassNotFoundException ce){
            ce.printStackTrace();
        }
    }

    public Integer LogInAuth(String name,String pw){
        try(Connection conn=DriverManager.getConnection(DB_URL,User,Password)){
            try (PreparedStatement ps=conn.prepareStatement("SELECT * from Clients WHERE name = ?")){
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

    public void AddClient(String name,String pw){
        try(Connection conn=DriverManager.getConnection(DB_URL,User,Password)){
            try(PreparedStatement ps=conn.prepareStatement("INSERT INTO Clients (name, password) VALUES (?,?)")){
                ps.setObject(1,name);
                ps.setObject(2,pw);
                ps.executeUpdate();
            }
        }catch (SQLException se){
            se.printStackTrace();
        }
    }

}
