package Model;

import java.io.Serializable;

public class User implements Serializable {
    private int id;
    private String fullName;
    private String gender;
    private String email;
    private String dob;
    private String password;

    private static User currentUser;

    public User() {}

    public User(String fullName, String gender, String email, String dob, String password) {
        this.fullName = fullName;
        this.gender = gender;
        this.email = email;
        this.dob = dob;
        this.password = password;
    }

    public User(int id, String fullName, String gender, String email, String dob, String password) {
        this.id = id;
        this.fullName = fullName;
        this.gender = gender;
        this.email = email;
        this.dob = dob;
        this.password = password;
    }

    public static void setCurrentUser(User user) {
        currentUser = user;
    }

    public static User getCurrentUser() {
        return currentUser;
    }

    public static void clearSession() {
        currentUser = null;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getDob() { return dob; }
    public void setDob(String dob) { this.dob = dob; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}