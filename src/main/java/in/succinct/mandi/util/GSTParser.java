package in.succinct.mandi.util;

import com.venky.core.string.StringUtil;
import com.venky.core.util.ObjectUtil;

import com.venky.swf.db.Database;
import in.succinct.plugins.ecommerce.db.model.attributes.AssetCode;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class GSTParser {
    public void parse(InputStream io) {
        Document document = Jsoup.parse(StringUtil.read(io));
        Element goods = document.getElementById("goods_table").selectFirst("tbody");
        Element services = document.getElementById("service_table").selectFirst("tbody");
        load(goods);
        //load(services);


    }
    public void load(Element element) {
        Elements trs= element.select("tr");
        List<AssetCode> assetCodeList = new ArrayList<>();
        for (Element tr : trs){
            Elements tds = tr.select("td");
            Element tdGstCode = tds.get(1);
            Element tdDescription = tds.get(2);
            Element tdCGst = tds.get(3);
            Element tdSGst = tds.get(4);
            Element tdIGst = tds.get(5);

            for (String code : splitCode(tdGstCode.html())){
                AssetCode assetCode = Database.getTable(AssetCode.class).newRecord();
                assetCode.setCode(code);
                assetCode.setGstPct(assetCode.getReflector().getJdbcTypeHelper().getTypeRef(Double.class).getTypeConverter().valueOf(tdIGst.text()));
                assetCode.setDescription(tdDescription.text());
                assetCode = Database.getTable(AssetCode.class).getRefreshed(assetCode);
                assetCode.save();
            }
        }
    }

    public List<String> splitCode(String inpCode){
        inpCode = inpCode.replaceAll("(\\[Except.*])","");
        inpCode = inpCode.replaceAll("(\\(Except.*\\))","");

        String[] codes = inpCode.split("(<br[ /]*>)|(,)|(or)");
        List<String> ret = new ArrayList<>();
        for (String code : codes){
            String c = code.replaceAll(" ","");
            if (!ObjectUtil.isVoid(c)){
                ret.add(c);
            }
        }
        return ret;
    }
}
