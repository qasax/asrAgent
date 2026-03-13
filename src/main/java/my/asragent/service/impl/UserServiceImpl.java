package my.asragent.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import my.asragent.entity.User;
import my.asragent.exception.BusinessException;
import my.asragent.exception.ErrorCode;
import my.asragent.mapper.UserMapper;
import my.asragent.model.user.UserQueryRequest;
import my.asragent.model.vo.LoginUserVO;
import my.asragent.model.vo.UserVO;
import my.asragent.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static my.asragent.constant.UserConstant.DEFAULT_ROLE;
import static my.asragent.constant.UserConstant.USER_LOGIN_STATE;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {
    @Override
    public String getEncryptPassword(String password) {
        final String SALT = "no_code_platform";
        return DigestUtils.md5DigestAsHex((SALT + password).getBytes());
    }

    @Override
    public BigInteger userRegister(String username, String email, String password, String checkPassword) {
        if (StrUtil.hasBlank(username, email, password, checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        if (username.length() < 2) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户名过短");
        }
        if (password.length() < 8 || checkPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码过短");
        }
        if (!password.equals(checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "两次输入的密码不一致");
        }
        if (!StrUtil.contains(email, "@")) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "邮箱格式错误");
        }

        QueryWrapper queryWrapper = new QueryWrapper();
        queryWrapper.eq("email", email).eq("username", username);
        long count = this.mapper.selectCountByQuery(queryWrapper);
        if (count > 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "邮箱或用户名已存在");
        }

        String encryptPassword = getEncryptPassword(password);
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(encryptPassword);
        user.setUserRole(DEFAULT_ROLE);
        user.setStatus(1);
        user.setCreatedAt(LocalDateTime.now());

        boolean saveResult = this.save(user);
        if (!saveResult) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "注册失败，数据库错误");
        }
        return user.getId();
    }

    @Override
    public LoginUserVO getLoginUserVO(User user) {
        if (user == null) {
            return null;
        }
        LoginUserVO loginUserVO = new LoginUserVO();
        BeanUtil.copyProperties(user, loginUserVO);
        return loginUserVO;
    }

    @Override
    public LoginUserVO userLogin(String email, String password, HttpServletRequest request) {
        if (StrUtil.hasBlank(email, password)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        if (password.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码错误");
        }

        String encryptPassword = getEncryptPassword(password);
        QueryWrapper queryWrapper = new QueryWrapper();
        queryWrapper.eq("email", email);
        queryWrapper.eq("password_hash", encryptPassword);
        User user = this.mapper.selectOneByQuery(queryWrapper);
        if (user == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户不存在或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "账号已禁用");
        }

        user.setLastLoginAt(LocalDateTime.now());
        this.updateById(user);
        request.getSession().setAttribute(USER_LOGIN_STATE, user);
        return this.getLoginUserVO(user);
    }

    @Override
    public User getLoginUser(HttpServletRequest request) {
        Object userObj = request.getSession().getAttribute(USER_LOGIN_STATE);
        User currentUser = (User) userObj;
        if (currentUser == null || currentUser.getId() == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        BigInteger userId = currentUser.getId();
        currentUser = this.getById(userId);
        if (currentUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        return currentUser;
    }

    @Override
    public User getLoginUserAllowNull(HttpServletRequest request) {
        Object userObj = request.getSession().getAttribute(USER_LOGIN_STATE);
        User currentUser = (User) userObj;
        if (currentUser == null || currentUser.getId() == null) {
            return null;
        }
        return this.getById(currentUser.getId());
    }

    @Override
    public boolean userLogout(HttpServletRequest request) {
        Object userObj = request.getSession().getAttribute(USER_LOGIN_STATE);
        if (userObj == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "未登录");
        }
        request.getSession().removeAttribute(USER_LOGIN_STATE);
        return true;
    }

    @Override
    public UserVO getUserVO(User user) {
        if (user == null) {
            return null;
        }
        UserVO userVO = new UserVO();
        BeanUtil.copyProperties(user, userVO);
        return userVO;
    }

    @Override
    public List<UserVO> getUserVOList(List<User> userList) {
        if (CollUtil.isEmpty(userList)) {
            return new ArrayList<>();
        }
        return userList.stream().map(this::getUserVO).collect(Collectors.toList());
    }

    @Override
    public QueryWrapper getQueryWrapper(UserQueryRequest userQueryRequest) {
        if (userQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        BigInteger id = userQueryRequest.getId();
        String username = userQueryRequest.getUsername();
        String email = userQueryRequest.getEmail();
        Integer status = userQueryRequest.getStatus();
        String userRole = userQueryRequest.getUserRole();
        String sortField = userQueryRequest.getSortField();
        String sortOrder = userQueryRequest.getSortOrder();

        QueryWrapper queryWrapper = QueryWrapper.create();
        if (id != null) {
            queryWrapper.eq("id", id);
        }
        if (StrUtil.isNotBlank(userRole)) {
            queryWrapper.eq("userRole", userRole);
        }
        if (status != null) {
            queryWrapper.eq("status", status);
        }
        if (StrUtil.isNotBlank(username)) {
            queryWrapper.like("username", username);
        }
        if (StrUtil.isNotBlank(email)) {
            queryWrapper.like("email", email);
        }

        if (StrUtil.isNotBlank(sortField) && isAllowedSortField(sortField)) {
            boolean asc = "ascend".equalsIgnoreCase(sortOrder);
            queryWrapper.orderBy(sortField, asc);
        }

        return queryWrapper;
    }

    private boolean isAllowedSortField(String sortField) {
        return "id".equals(sortField)
                || "username".equals(sortField)
                || "email".equals(sortField)
                || "status".equals(sortField)
                || "userRole".equals(sortField)
                || "createdAt".equals(sortField)
                || "lastLoginAt".equals(sortField);
    }
}
