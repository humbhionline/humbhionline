package in.succinct.mandi.db.model.blog.rss;

import com.venky.swf.db.annotations.column.COLUMN_NAME;
import com.venky.swf.db.annotations.column.IS_NULLABLE;
import com.venky.swf.db.annotations.column.UNIQUE_KEY;
import com.venky.swf.db.annotations.column.ui.PROTECTION;
import com.venky.swf.db.annotations.column.ui.WATERMARK;
import com.venky.swf.db.annotations.model.ORDER_BY;
import com.venky.swf.db.model.Model;

import java.sql.Date;

@ORDER_BY("PUBLISHED_ON DESC")
public interface Post extends Model {
    @IS_NULLABLE(false)
    @UNIQUE_KEY
    public Long getSourceId();
    public void setSourceId(Long id);
    public Channel getSource();

    @UNIQUE_KEY
    @WATERMARK("Enter relative url of the blog link")
    public String getLink();
    public void setLink(String link);

    public Date getPublishedOn();
    public void setPublishedOn(Date publishedOn);

    public String getTitle();
    public void setTitle(String title);


    public String getDescription();
    public void setDescription(String description);

    public String getAuthor();
    public void setAuthor(String author);


    @COLUMN_NAME("LINK")
    @PROTECTION
    public String getGuid();
    public void setGuid(String guid);

}
