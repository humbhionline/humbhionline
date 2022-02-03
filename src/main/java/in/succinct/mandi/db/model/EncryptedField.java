package in.succinct.mandi.db.model;

import com.venky.swf.db.model.Model;

public interface EncryptedField extends Model {
    public Long getEncryptedModelId();
    public void setEncryptedModelId(Long id);
    public EncryptedModel getEncryptedModel();

    public String getFieldName();
    public void setFieldName(String encryptedFieldName);

}
