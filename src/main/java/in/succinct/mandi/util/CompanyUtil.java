package in.succinct.mandi.util;

import com.venky.core.collections.SequenceSet;
import com.venky.core.util.ObjectUtil;
import com.venky.swf.db.Database;
import com.venky.swf.db.model.reflection.ModelReflector;
import com.venky.swf.path._IPath;
import com.venky.swf.plugins.security.db.model.Role;
import com.venky.swf.plugins.security.db.model.UserRole;
import com.venky.swf.pm.DataSecurityFilter;
import com.venky.swf.routing.Config;
import com.venky.swf.sql.Expression;
import com.venky.swf.sql.Operator;
import com.venky.swf.sql.Select;
import in.succinct.mandi.db.model.User;
import in.succinct.plugins.ecommerce.db.model.participation.Company;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.stream.Collectors;

public class CompanyUtil {
    public static Long getCompanyId(){
        String domainName = getFQDomainName();
        List<Company> companies = new ArrayList<>();
        if (!ObjectUtil.isVoid(domainName)){
            companies = new Select().from(in.succinct.plugins.ecommerce.db.model.participation.Company.class).
                    where(new Expression(ModelReflector.instance(in.succinct.plugins.ecommerce.db.model.participation.Company.class).getPool(),
                            "DOMAIN_NAME", Operator.LK, "%"+getFQDomainName()+"%")).execute();
        }

        if (companies.isEmpty()){
            com.venky.swf.db.model.User sessionUser = Database.getInstance().getCurrentUser();

            if (sessionUser != null){
                User user = sessionUser.getRawRecord().getAsProxy(User.class);
                if (user.getCompanyId() != null){
                    companies.add(user.getCompany().getRawRecord().getAsProxy(in.succinct.plugins.ecommerce.db.model.participation.Company.class));
                }
            }
        }
        if (companies.isEmpty()){
            companies = new Select().from(in.succinct.plugins.ecommerce.db.model.participation.Company.class).execute(2);
            if (companies.size() > 1){
                companies.clear();
            }
        }
        SequenceSet<Long> companyIds = DataSecurityFilter.getIds(companies);
        Long companyId = null;
        if (!companyIds.isEmpty()){
            companyId = companyIds.get(0);
        }
        return companyId;
    }
    public static String getFQDomainName() {
        _IPath path = Database.getInstance().getContext(_IPath.class.getName());
        String domainName = null;
        if (path == null){
            domainName = Config.instance().getProperty("swf.host","");
        }else {
            domainName = path.getRequest().getServerName();
        }
        List<String> domainParts = new ArrayList<>();
        StringTokenizer tok = new StringTokenizer(domainName,".");
        while (tok.hasMoreTokens()){
            domainParts.add(tok.nextToken());
        }
        while (domainParts.size() > 2){
            domainParts.remove(0);
        }
        StringBuilder fQdomainName = new StringBuilder();
        for (String part: domainParts){
            if(fQdomainName.length() > 0){
                fQdomainName.append(".");
            }
            fQdomainName.append(part);
        }
        return fQdomainName.toString();

    }
    
    public static boolean isOldDataEntryUser(com.venky.swf.db.model.User aUser){
        if (aUser == null){
            return false;
        }
        User user = aUser.getRawRecord().getAsProxy(User.class);
        
        List<UserRole> userRoleList = user.getUserRoles();
        com.venky.swf.plugins.security.db.model.Role roleDataEntry = com.venky.swf.plugins.security.db.model.Role.getRole("DATAENTRY");
        boolean dataEntry = false;
        if (roleDataEntry != null){
            for (UserRole userRole : userRoleList){
                if (userRole.getRoleId() == roleDataEntry.getId()){
                    dataEntry = true;
                    break;
                }
            }
        }
        return dataEntry && user.getName().startsWith("old");
    }


    public static List<User> getAdminUsers() {

        Role role = Role.getRole("ADMIN");
        if (role != null){
            return role.getUserRoles().stream().map(ur->ur.getUser().getRawRecord().getAsProxy(User.class)).collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}
