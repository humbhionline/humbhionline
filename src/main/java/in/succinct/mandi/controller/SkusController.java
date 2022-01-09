package in.succinct.mandi.controller;

import com.ondc.client.utils.OCRUtils;
import com.venky.cache.Cache;
import com.venky.core.collections.SequenceSet;
import com.venky.core.string.StringUtil;
import com.venky.core.util.Bucket;
import com.venky.core.util.ObjectUtil;
import com.venky.swf.controller.annotations.RequireLogin;
import com.venky.swf.db.annotations.column.ui.mimes.MimeType;
import com.venky.swf.db.model.reflection.ModelReflector;
import com.venky.swf.path.Path;
import com.venky.swf.plugins.lucene.index.LuceneIndexer;
import com.venky.swf.routing.Config;
import com.venky.swf.sql.Conjunction;
import com.venky.swf.sql.Expression;
import com.venky.swf.sql.Operator;
import com.venky.swf.sql.Select;
import com.venky.swf.views.BytesView;
import com.venky.swf.views.View;
import in.succinct.beckn.Descriptor;
import in.succinct.beckn.Images;
import in.succinct.beckn.Item;
import in.succinct.beckn.Items;
import in.succinct.mandi.db.model.Sku;
import in.succinct.plugins.ecommerce.db.model.attachments.Attachment;
import org.apache.lucene.search.Query;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

public class SkusController extends in.succinct.plugins.ecommerce.controller.SkusController<Sku> {
    public SkusController(Path path) {
        super(path);
    }

    @RequireLogin(false)
    public View image_search() throws Exception{
        String strings = getStrings(StringUtil.readBytes((InputStream)getPath().getFormFields().get("file")));
        return open_search(strings);
    }
    public String getStrings(byte[] imageArray){
        String imageUrl = "data:image/png;base64," +
                Base64.getEncoder().encodeToString(imageArray);
        return OCRUtils.parseImage(imageUrl);
    }
    @RequireLogin(false)
    public View index(){
        return super.index();
    }
    @RequireLogin(false)
    public View open_search(){
        Object o = getPath().getFormFields().get("q");
        String q = null;
        if (o instanceof InputStream){
            q = StringUtil.read((InputStream) o);
        }else {
            q = StringUtil.valueOf(o);
        }
        return open_search(q);
    }
    @RequireLogin(false)
    public View open_search(String q){
        List<Sku> output = new ArrayList<>();
        if (!ObjectUtil.isVoid(q)){
            if (!q.matches("^.*[,:*+-^*]+.*")){
                q = q.replaceAll("[^A-z,0-9 ]"," ");
            }
            StringTokenizer tokenizer = new StringTokenizer(q);
            int index = tokenizer.countTokens();
            LuceneIndexer indexer = LuceneIndexer.instance(getModelClass());
            Map<Long,Bucket> weight = new Cache<Long, Bucket>() {
                @Override
                protected Bucket getValue(Long aLong) {
                    return new Bucket();
                }
            };
            int maxRecords = 100;
            while (tokenizer.hasMoreTokens()){
                Query qry = indexer.constructQuery(tokenizer.nextToken()+"*");
                List<Long> ids = indexer.findIds(qry, 2 * maxRecords);
                if (!ids.isEmpty() && ids.size() < maxRecords){
                    // Reasonable Selectivity.!!
                    ids.forEach(id->weight.get(id).increment());
                }
            }

            Select sel = new Select().from(getModelClass()).where(new Expression(getReflector().getPool(), Conjunction.AND)
                    .add(Expression.createExpression(getReflector().getPool(), "ID", Operator.IN, weight.keySet().toArray()))
                    .add(getWhereClause()));

            List<Sku> records = sel.execute(getModelClass(), 0 , getFilter());
            records.sort((o1, o2) -> weight.get(o2.getId()).intValue()  - weight.get(o1.getId()).intValue());
            if (records.size() < 5){
                output = records;
            }else {
                for (int i = 0 ; i < 5 ; i ++ ){
                    output.add(records.get(i));
                }
            }
        }

        Items items = new Items();
        for (Sku record: output){
            Item item = new Item();
            Descriptor descriptor = new Descriptor();
            item.setDescriptor(descriptor);
            descriptor.setName(record.getName());
            if (!ObjectUtil.isVoid(record.getSkuCode())){
                descriptor.setCode(record.getSkuCode());
            }
            descriptor.setShortDesc(record.getName());
            descriptor.setLongDesc(record.getName());
            Images images = new Images();
            for (Attachment attachment : record.getAttachments()){
                images.add(Config.instance().getServerBaseUrl() + attachment.getAttachmentUrl());
            }
            if (images.size() > 0) {
                descriptor.setImages(images);
            }
            item.setId(String.valueOf(record.getId()));
            items.add(item);
        }
        return new BytesView(getPath(),items.toString().getBytes(StandardCharsets.UTF_8), MimeType.APPLICATION_JSON);
    }

}
