package in.succinct.mandi.controller;

import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient.Mqtt3SubscribeAndCallbackBuilder.Call.Ex;
import com.venky.core.collections.SequenceMap;
import com.venky.core.string.StringUtil;
import com.venky.geo.GeoCoordinate;
import com.venky.swf.controller.ModelController;
import com.venky.swf.controller.annotations.RequireLogin;
import com.venky.swf.db.Database;
import com.venky.swf.db.model.Model;
import com.venky.swf.db.model.reflection.ModelReflector;
import com.venky.swf.integration.FormatHelper;
import com.venky.swf.path.Path;
import com.venky.swf.sql.Conjunction;
import com.venky.swf.sql.Expression;
import com.venky.swf.sql.Operator;
import com.venky.swf.sql.Select;
import com.venky.swf.views.View;
import com.venky.swf.views.model.FileUploadView;
import in.succinct.mandi.db.model.Facility;
import in.succinct.mandi.db.model.Sku;
import in.succinct.mandi.db.model.User;
import in.succinct.mandi.util.CompanyUtil;
import in.succinct.plugins.ecommerce.db.model.attachments.Attachment;
import in.succinct.plugins.ecommerce.db.model.catalog.UnitOfMeasure;
import in.succinct.plugins.ecommerce.db.model.inventory.AdjustmentRequest;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.json.simple.JSONObject;

import javax.servlet.http.HttpServletRequest;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.StringTokenizer;

public class FacilitiesController extends LocalSearchController<Facility> {
    public FacilitiesController(Path path) {
        super(path);
    }

    /* Is permission controlled */
    @RequireLogin
    public View publish(long id){
        Facility f = Database.getTable(Facility.class).get(id);
        f.publish();
        return show(f);
    }

    @RequireLogin
    public View unpublish(long id){
        Facility f = Database.getTable(Facility.class).get(id);
        f.unpublish();
        return show(f);
    }

    @RequireLogin
    public View startCount(long id){
        if (Database.getTable(Facility.class).get(id) == null){
            throw new RuntimeException("Invalid facility Id");
        }
        Facility f = Database.getTable(Facility.class).lock(id);
        f.getInventoryList().forEach(i->{
            i.setInfinite(false);
            i.adjust(-1*i.getQuantity(),"StartCount"); //Unpublish inventory.
        });
        return getReturnIntegrationAdaptor().createStatusResponse(getPath(),null);
    }

    @RequireLogin
    public View mine(){
        User user = getPath().getSessionUser().getRawRecord().getAsProxy(User.class);
        List<Facility> facilityList = new Select().from(Facility.class).where(new Expression(getReflector().getPool(),"ID", Operator.IN,user.getOperatingFacilityIds().toArray())).execute();
        return list(facilityList,true);
    }

    @Override
    protected Expression getWhereClause() {
        Expression complete = super.getWhereClause();
        GeoCoordinate serviceLocation = getServiceRequirementLocation();

        Expression where = new Expression(getReflector().getPool(), Conjunction.OR);
        if (serviceLocation != null) {
            where.add(getFacilityWhereClause(serviceLocation, false));
        }

        List<Long> myFacilityIds = getCurrentUserOperatedFacilityIds();
        if (!myFacilityIds.isEmpty()){
            where.add(new Expression(getReflector().getPool(),"ID",Operator.IN,myFacilityIds.toArray()));
        }
        complete.add(where);
        return complete;
    }

    /* Keep in sync with apicontroller*/
    @Override
    protected Map<Class<? extends Model>, List<String>> getIncludedModelFields() {
        Map<Class<? extends Model>, List<String>> map =  super.getIncludedModelFields();
        /*
        if (getPath().action().equals("show") || getPath().action().equals("importInventory")) {
            map.put(Inventory.class, ModelReflector.instance(Inventory.class).getFields());
            List<String> itemFields = ModelReflector.instance(Item.class).getUniqueFields();
            itemFields.add("ASSET_CODE_ID");
            itemFields.add("ID");
            map.put(Item.class, itemFields);

            List<String> skuFields = ModelReflector.instance(Sku.class).getUniqueFields();
            skuFields.add("MAX_RETAIL_PRICE");
            skuFields.add("TAX_RATE");
            skuFields.add("ID");

            map.put(Sku.class,skuFields);
            map.put(AssetCode.class, Arrays.asList("CODE","LONG_DESCRIPTION","GST_PCT"));
        }
         */
        map.put(User.class,ModelReflector.instance(User.class).getUniqueFields());
        map.get(User.class).addAll(Arrays.asList("ID","NAME_AS_IN_BANK_ACCOUNT","VIRTUAL_PAYMENT_ADDRESS"));

        map.put(Attachment.class,Arrays.asList("ID","ATTACHMENT_URL"));
        return map;
    }

    @Override
    protected Map<Class<? extends Model>, List<Class<? extends Model>>> getConsideredChildModels() {
        Map<Class<? extends Model>,List<Class<? extends Model>>> consideredModels = super.getConsideredChildModels();
        consideredModels.get(Sku.class).add(Attachment.class);
        consideredModels.get(Facility.class).add(Attachment.class);
        return consideredModels;
    }

    @Override
    @RequireLogin(false)
    public View index() {
        return super.index();
    }

    @Override
    @RequireLogin(false)
    public View show(long id) {
        return super.show(id);
    }



    public View importInventory(long id) throws Exception {
        ensureUI();
        HttpServletRequest request = getPath().getRequest();

        if (request.getMethod().equalsIgnoreCase("GET")) {
            return dashboard(new FileUploadView(getPath()));
        } else {
            Facility f = Database.getTable(getModelClass()).get(id);
            Map<String, Object> formFields = getFormFields();
            String sFileData = (String) formFields.get("datafile");

            InputStream in = null;

            String[] parts = sFileData.split("^data:[^;]+;base64,");
            if (parts.length == 2){
                in = new ByteArrayInputStream(Base64.getDecoder().decode(parts[1]));
            }
            if (in == null) {
                throw new RuntimeException("Nothing uploaded!");
            }

            Workbook book = new XSSFWorkbook(in);
            Sheet sheet = book.getSheetAt(0);
            Row header = null;
            Map<String, Integer> headingIndexMap = new HashMap<>();
            FormatHelper<JSONObject> helper = FormatHelper.instance(new JSONObject());
            for (Iterator<Row> i = sheet.iterator(); i.hasNext(); ) {
                Row row = i.next();
                if (header == null) {
                    header = row;
                    headingIndexMap = headingIndexMap(header);
                    continue;
                }
                FormatHelper<JSONObject> oneHelper = FormatHelper.instance(helper.createArrayElement("AdjustmentRequest"));

                makeJson(oneHelper, headingIndexMap, row);
                FormatHelper<JSONObject> invHelper = FormatHelper.instance(oneHelper.getElementAttribute("Inventory"));
                FormatHelper<JSONObject> skuHelper = FormatHelper.instance(invHelper.getElementAttribute("Sku"));
                FormatHelper<JSONObject> itemHelper = FormatHelper.instance(skuHelper.getElementAttribute("Item"));
                FormatHelper<JSONObject> packHelper = FormatHelper.instance(skuHelper.getElementAttribute("PackagingUOM"));

                oneHelper.setAttribute("AdjustmentQuantity","0");
                invHelper.setAttribute("CompanyId", StringUtil.valueOf(CompanyUtil.getCompanyId()));
                invHelper.setAttribute("FacilityId",StringUtil.valueOf(f.getId()));
                itemHelper.setAttribute("CompanyId", StringUtil.valueOf(CompanyUtil.getCompanyId()));
                skuHelper.setAttribute("CompanyId", StringUtil.valueOf(CompanyUtil.getCompanyId()));
                skuHelper.setAttribute("Published","N");
                packHelper.setAttribute("Measures", UnitOfMeasure.MEASURES_PACKAGING);

            }
            List<AdjustmentRequest> requests = AdjustmentRequest.adjust(helper);
            return show(f.getId());
        }
    }

    private void makeJson(FormatHelper<JSONObject> helper, Map<String, Integer> headingIndexMap, Row row) {
        for (Entry<String,Integer> entry : headingIndexMap.entrySet()){
            String key = entry.getKey();
            int index = entry.getValue();
            Cell cell  = row.getCell(index);
            String value = cell == null ? null : cell.getCellType() == CellType.NUMERIC ? StringUtil.valueOf(cell.getNumericCellValue()) : cell.getStringCellValue();
            StringTokenizer tokenizer = new StringTokenizer(key,".");

            FormatHelper<JSONObject> tmp =  helper;
            while (tokenizer.hasMoreTokens()) {
                String k1 = tokenizer.nextToken();
                if (tokenizer.hasMoreTokens()){
                    tmp = FormatHelper.instance(tmp.createElementAttribute(k1));
                }else {
                    tmp.setAttribute(k1, StringUtil.valueOf(value));
                }
            }
        }

    }

    private Map<String,Integer> headingIndexMap(Row header){
        Map<String,Integer> headingIndexMap = new SequenceMap<String, Integer>();
        for (int i = 0 ; i < header.getLastCellNum() ; i ++ ){
            headingIndexMap.put(header.getCell(i).getStringCellValue(), i);
        }
        return headingIndexMap;
    }
}
