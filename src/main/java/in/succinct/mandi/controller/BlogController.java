package in.succinct.mandi.controller;

import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient.Mqtt3SubscribeAndCallbackBuilder.Call.Ex;
import com.venky.core.util.ObjectUtil;
import com.venky.swf.controller.Controller;
import com.venky.swf.controller.annotations.RequireLogin;
import com.venky.swf.db.annotations.column.ui.mimes.MimeType;
import com.venky.swf.path.Path;
import com.venky.swf.routing.Config;
import com.venky.swf.sql.Expression;
import com.venky.swf.sql.Operator;
import com.venky.swf.sql.Select;
import com.venky.swf.views.BytesView;
import com.venky.swf.views.View;
import com.venky.xml.XMLDocument;
import com.venky.xml.XMLElement;
import in.succinct.mandi.db.model.blog.rss.Channel;

import java.util.List;

public class BlogController extends Controller {
    public BlogController(Path path) {
        super(path);
    }
    @RequireLogin(false)
    public View rss(){
        return rss(null);
    }
    @RequireLogin(false)
    public View rss(String name){
        Select select = new Select().from(Channel.class);
        if (!ObjectUtil.isVoid(name)){
            select.where(new Expression(select.getPool(),"NAME", Operator.EQ,name));
        }
        List<Channel> channelList = select.execute();
        XMLDocument document = new XMLDocument("rss");
        XMLElement rss = document.getDocumentRoot();
        rss.setAttribute("version","2.0");
        for (Channel channel : channelList){
            XMLElement elemChannel = rss.createChildElement("channel");
            elemChannel.createChildElement("title").setNodeValue(channel.getName());
            elemChannel.createChildElement("link").setNodeValue(Config.instance().getServerBaseUrl());
            elemChannel.createChildElement("description").setNodeValue(channel.getDescription());
            elemChannel.createChildElement("pubDate").setNodeValue(Channel.RSS_DATE_FORMATTER.format(channel.getPublishedOn()));
            channel.getPosts().forEach(post->{
                XMLElement item = elemChannel.createChildElement("item");
                item.createChildElement("title").setNodeValue(post.getTitle());
                item.createChildElement("link").setNodeValue(Config.instance().getServerBaseUrl() + post.getLink());
                item.createChildElement("description").setNodeValue(post.getDescription());
                item.createChildElement("pubDate").setNodeValue(Channel.RSS_DATE_FORMATTER.format(post.getPublishedOn()));
                item.createChildElement("guid").setNodeValue(post.getLink());
                XMLElement source= item.createChildElement("source");
                source.setNodeValue(channel.getName());
                source.setAttribute("url",Config.instance().getServerBaseUrl() + getPath().controllerPath() + "/" + getPath().action() + "/" + channel.getName());
                source.setAttribute("author",post.getAuthor());
            });
        }

        return new BytesView(getPath(),document.toString().getBytes(), MimeType.APPLICATION_XML);

    }
}
