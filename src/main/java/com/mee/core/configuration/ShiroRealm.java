package com.mee.core.configuration;

import com.mee.common.service.LogServiceImpl;
import com.mee.common.util.HttpUtil;
import com.mee.core.dao.DBSQLDao;
import com.mee.sys.entity.SysMenu;
import com.mee.sys.entity.SysRole;
import com.mee.sys.entity.SysUser;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.SimpleAuthenticationInfo;
import org.apache.shiro.authz.AuthorizationException;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.authz.SimpleAuthorizationInfo;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.subject.PrincipalCollection;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @description 身份校验核心类
 * @author shadow
 *
 */
// @Service
public class ShiroRealm extends AuthorizingRealm {

    @Resource
    private DBSQLDao dbSQLDao;

    @Resource
    private LogServiceImpl logService;

    /**
     * 认证登陆
     */
    @Override
    protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken token) throws AuthenticationException {
        //加这一步的目的是在Post请求的时候会先进认证，然后在到请求
        if (token.getPrincipal() == null) {
            return null;
        }
        String username = (String) token.getPrincipal();
        // String password = new String((char[]) token.getCredentials());
        // 通过username从数据库中查找 User对象，如果找到，没找到.
        // 实际项目中，这里可以根据实际情况做缓存，如果不做，Shiro自己也是有时间间隔机制，2分钟内不会重复执行该方法

        Map<String,Object> params = new HashMap<String,Object>(1,1){{
            put("user_name",username);
        }};
        List<SysUser> sysUserList = dbSQLDao.query("com.mee.xml.SysUser.findList",params);
        if(sysUserList.size()!=1){
            return null;
        }
        SysUser sysUser = sysUserList.get(0);
        if (sysUser == null)
            return null;
        else{
            // record log
            logService.log(sysUser.getUser_id(),1,username, HttpUtil.getRemoteAddr(ShiroUtils.getRequest()),"user login record");

            SimpleAuthenticationInfo authenticationInfo = new SimpleAuthenticationInfo(
                    sysUser, // 用户对象
                    sysUser.getPassword(), // 密码
                    getName() // realm name
            );
            return authenticationInfo;
        }

    }

    /**
     * 授权查询回调函数, 进行鉴权但缓存中无用户的授权信息时调用.
     */
    @Override
    protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principals) {
        if(principals == null){
            throw new AuthorizationException("principals should not be null");
        }
        SimpleAuthorizationInfo authorizationInfo = new SimpleAuthorizationInfo();
        SysUser userinfo  = (SysUser)principals.getPrimaryPrincipal();
        // 通过中间表查询用户所属角色
        Map<String,Object> params = new HashMap<String,Object>(1,1){{
            put("user_id",userinfo.getUser_id());
        }};
        List<SysRole> sysRoles= dbSQLDao.query("com.mee.xml.SysRole.findByUserId",params);
        for(SysRole userrole:sysRoles){
            String rolid=userrole.getId();//角色id
            authorizationInfo.addRole(userrole.getRole_name());//添加角色名字
            // 查询菜单权限
            Map<String,Object> queryParams = new HashMap<String,Object>(1,1){{
                put("role_id",rolid);
            }};
            List<SysMenu> sysMenus= dbSQLDao.query("com.mee.xml.SysMenu.findByRoleID",queryParams);
            for(SysMenu p:sysMenus){
                //System.out.println("角色下面的权限:"+gson.toJson(p));
                if(!StringUtils.isEmpty(p.getCode())){
                    authorizationInfo.addStringPermission(p.getCode());
                }

            }
        }
        return authorizationInfo;
    }

    /**
     * 清理缓存权限
     */
    public void clearCachedAuthorizationInfo(){
        this.clearCachedAuthorizationInfo(SecurityUtils.getSubject().getPrincipals());
    }

}