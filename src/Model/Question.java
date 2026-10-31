package Model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Question implements Serializable {
    private int id;
    private int quizId;
    private String content;
    private String imageUrl;
    private List<Option> options = new ArrayList<>();

    public Question() {}

    public Question(String content, String imageUrl, List<Option> options) {
        this.content = content;
        this.imageUrl = imageUrl;
        this.options = options;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getQuizId() { return quizId; }
    public void setQuizId(int quizId) { this.quizId = quizId; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public List<Option> getOptions() { return options; }
    public void setOptions(List<Option> options) { this.options = options; }

    @Override
    public String toString() {
        return content.length() > 35 ? content.substring(0, 35) + "..." : content;
    }
}