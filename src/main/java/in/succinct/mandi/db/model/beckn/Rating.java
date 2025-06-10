package in.succinct.mandi.db.model.beckn;

import com.venky.swf.db.annotations.column.UNIQUE_KEY;
import com.venky.swf.db.annotations.column.validations.Enumeration;
import com.venky.swf.db.model.Model;

public interface Rating extends Model {
    @UNIQUE_KEY
    @Enumeration(enumClass = "in.succinct.beckn.Rating$RatingCategory")
    public String getRatingCategory();
    public void setRatingCategory(String ratingCategory);
    
    @UNIQUE_KEY
    String getTransactionId();
    void setTransactionId(String transactionId);
    

    @UNIQUE_KEY
    public String getObjectId();
    public void setObjectId(String objectId);

    public int getValue();
    public void setValue(int value);


}
