package in.succinct.mandi.db.model.blog.rss;

import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient.Mqtt3SubscribeAndCallbackBuilder.Call.Ex;
import com.venky.core.date.DateUtils;
import com.venky.swf.db.model.reflection.ModelReflector;
import com.venky.swf.db.table.ModelImpl;
import com.venky.swf.sql.Expression;
import com.venky.swf.sql.Operator;
import com.venky.swf.sql.Select;

import java.sql.Date;
import java.util.List;

public class ChannelImpl extends ModelImpl<Channel> {
    public ChannelImpl(Channel channel){
        super(channel);
    }
    public ChannelImpl(){
        super();
    }

    Date publishedOn = null;
    public Date getPublishedOn(){
        if (getProxy().getRawRecord().isNewRecord()){
            return null;
        }
        if (publishedOn == null){
            List<Post> posts = new Select().from(Post.class).
                    where(new Expression(ModelReflector.instance(Post.class).getPool(),"SOURCE_ID",
                            Operator.EQ,getProxy().getId())).orderBy("CREATED_AT DESC").execute(1);
            if (posts.isEmpty()){
                publishedOn = new Date(DateUtils.getStartOfDay(getProxy().getUpdatedAt().getTime()));
            }else {
                publishedOn = posts.get(0).getPublishedOn();
            }

        }
        return publishedOn;
    }


}
