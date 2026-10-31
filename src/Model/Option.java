package Model;

import java.io.Serializable;

public class Option implements Serializable {
    private int id;
    private int questionId;
    private String content;
    private boolean isCorrect;

    public Option() {}

    public Option(String content, boolean isCorrect) {
        this.content = content;
        this.isCorrect = isCorrect;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getQuestionId() { return questionId; }
    public void setQuestionId(int questionId) { this.questionId = questionId; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public boolean isCorrect() { return isCorrect; }
    public void setCorrect(boolean correct) { isCorrect = correct; }
}