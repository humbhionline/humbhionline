package in.succinct.mandi.db.model.blog.rss;

import com.venky.swf.db.annotations.column.COLUMN_DEF;
import com.venky.swf.db.annotations.column.IS_NULLABLE;
import com.venky.swf.db.annotations.column.IS_VIRTUAL;
import com.venky.swf.db.annotations.column.defaulting.StandardDefault;
import com.venky.swf.db.annotations.column.ui.PROTECTION;
import com.venky.swf.db.model.Model;

import java.sql.Date;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.List;


public interface Channel extends Model {
    public String getName();
    public void setName(String name);

    public String getDescription();
    public void setDescription(String description);

    @COLUMN_DEF(value = StandardDefault.SOME_VALUE,args = "en-us")
    @PROTECTION
    public String getLanguage();
    public void setLanguage(String language);

    final DateFormat RSS_DATE_FORMATTER = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz");

    @IS_VIRTUAL
    public Date getPublishedOn();

    public List<Post> getPosts();

}
