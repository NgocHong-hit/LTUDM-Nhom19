package Model;

public class ExamResult {
    private int rank;
    private String userName;
    private double score;
    private int completionTimeSeconds;
    private String submittedAt;

    public ExamResult() {}

    // Chỉ sử dụng các Getter / Setter cơ bản kiểu String/int/double
    public int getRank() { return rank; }
    public void setRank(int rank) { this.rank = rank; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public double getScore() { return score; }
    public void setScore(double score) { this.score = score; }

    public int getCompletionTimeSeconds() { return completionTimeSeconds; }
    public void setCompletionTimeSeconds(int completionTimeSeconds) { this.completionTimeSeconds = completionTimeSeconds; }

    public String getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(String submittedAt) { this.submittedAt = submittedAt; }
}