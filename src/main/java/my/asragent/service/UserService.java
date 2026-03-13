package my.asragent.service;

import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import jakarta.servlet.http.HttpServletRequest;
import my.asragent.entity.User;
import my.asragent.model.user.UserQueryRequest;
import my.asragent.model.vo.LoginUserVO;
import my.asragent.model.vo.UserVO;

import java.util.List;
import java.math.BigInteger;

/**
 *  服务层。
 *
 * @author zhangfajin
 */
public interface UserService extends IService<User> {

    String getEncryptPassword(String password);

    BigInteger userRegister(String username, String email, String password, String checkPassword);

    LoginUserVO getLoginUserVO(User user);

    LoginUserVO userLogin(String email, String password, HttpServletRequest request);

    User getLoginUser(HttpServletRequest request);

    User getLoginUserAllowNull(HttpServletRequest request);

    boolean userLogout(HttpServletRequest request);

    UserVO getUserVO(User user);

    List<UserVO> getUserVOList(List<User> userList);

    QueryWrapper getQueryWrapper(UserQueryRequest userQueryRequest);
}
