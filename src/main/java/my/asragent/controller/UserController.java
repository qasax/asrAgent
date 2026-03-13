package my.asragent.controller;

import cn.hutool.core.bean.BeanUtil;
import com.mybatisflex.core.paginate.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import my.asragent.annotation.AuthCheck;
import my.asragent.common.BaseResponse;
import my.asragent.common.DeleteRequest;
import my.asragent.common.ResultUtils;
import my.asragent.constant.UserConstant;
import my.asragent.entity.User;
import my.asragent.exception.BusinessException;
import my.asragent.exception.ErrorCode;
import my.asragent.exception.ThrowUtils;
import my.asragent.model.user.UserAddRequest;
import my.asragent.model.user.UserLoginRequest;
import my.asragent.model.user.UserQueryRequest;
import my.asragent.model.user.UserRegisterRequest;
import my.asragent.model.user.UserUpdateRequest;
import my.asragent.model.vo.LoginUserVO;
import my.asragent.model.vo.UserVO;
import my.asragent.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/user")
@Tag(name = "用户管理", description = "用户注册、登录与管理接口")
public class UserController {

    @Autowired
    private UserService userService;

    // region 基础 CRUD（可选）

    @PostMapping("save")
    public boolean save(@RequestBody User user) {
        return userService.save(user);
    }

    @DeleteMapping("remove/{id}")
    public boolean remove(@PathVariable BigInteger id) {
        return userService.removeById(id);
    }

    @PutMapping("update")
    public boolean update(@RequestBody User user) {
        return userService.updateById(user);
    }

    @GetMapping("list")
    public List<User> list() {
        return userService.list();
    }

    @GetMapping("getInfo/{id}")
    public User getInfo(@PathVariable BigInteger id) {
        return userService.getById(id);
    }

    @GetMapping("page")
    public Page<User> page(Page<User> page) {
        return userService.page(page);
    }

    // endregion

    // region 登录相关

    @Operation(summary = "用户注册", description = "通过用户名、邮箱与密码注册用户，返回新用户 ID")
    @PostMapping("register")
    public BaseResponse<BigInteger> userRegister(@RequestBody UserRegisterRequest userRegisterRequest) {
        ThrowUtils.throwIf(userRegisterRequest == null, ErrorCode.PARAMS_ERROR);
        String username = userRegisterRequest.getUsername();
        String email = userRegisterRequest.getEmail();
        String password = userRegisterRequest.getPassword();
        String checkPassword = userRegisterRequest.getCheckPassword();
        BigInteger result = userService.userRegister(username, email, password, checkPassword);
        return ResultUtils.success(result);
    }

    @Operation(summary = "用户登录", description = "通过邮箱和密码登录，返回脱敏后的登录用户信息")
    @PostMapping("/login")
    public BaseResponse<LoginUserVO> userLogin(@RequestBody UserLoginRequest userLoginRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(userLoginRequest == null, ErrorCode.PARAMS_ERROR);
        String email = userLoginRequest.getEmail();
        String password = userLoginRequest.getPassword();
        LoginUserVO loginUserVO = userService.userLogin(email, password, request);
        return ResultUtils.success(loginUserVO);
    }

    @Operation(summary = "获取当前登录用户", description = "获取当前已登录用户的脱敏信息")
    @GetMapping("/get/login")
    public BaseResponse<LoginUserVO> getLoginUser(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(userService.getLoginUserVO(loginUser));
    }

    @Operation(summary = "用户注销", description = "退出当前登录状态")
    @PostMapping("/logout")
    public BaseResponse<Boolean> userLogout(HttpServletRequest request) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        boolean result = userService.userLogout(request);
        return ResultUtils.success(result);
    }

    // endregion

    // region 管理员增删改查

    @Operation(summary = "创建用户（管理员）", description = "管理员创建新用户，默认密码为 12345678")
    @PostMapping("/add")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<BigInteger> addUser(@RequestBody UserAddRequest userAddRequest) {
        ThrowUtils.throwIf(userAddRequest == null, ErrorCode.PARAMS_ERROR);
        User user = new User();
        BeanUtil.copyProperties(userAddRequest, user);
        if (user.getUsername() == null || user.getUsername().isBlank()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户名不能为空");
        }
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "邮箱不能为空");
        }
        if (user.getUserRole() == null || user.getUserRole().isBlank()) {
            user.setUserRole(UserConstant.DEFAULT_ROLE);
        }
        if (user.getStatus() == null) {
            user.setStatus(1);
        }
        user.setCreatedAt(LocalDateTime.now());
        final String DEFAULT_PASSWORD = "12345678";
        String encryptPassword = userService.getEncryptPassword(DEFAULT_PASSWORD);
        user.setPasswordHash(encryptPassword);
        boolean result = userService.save(user);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(user.getId());
    }

    @Operation(summary = "根据 ID 获取用户（管理员）", description = "管理员根据用户 ID 获取完整用户信息（未脱敏）")
    @GetMapping("/get")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<User> getUserById(
            @Parameter(description = "用户 ID", required = true) @RequestParam BigInteger id) {
        ThrowUtils.throwIf(id == null || id.compareTo(BigInteger.ZERO) <= 0, ErrorCode.PARAMS_ERROR);
        User user = userService.getById(id);
        ThrowUtils.throwIf(user == null, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(user);
    }

    @Operation(summary = "根据 ID 获取用户信息（脱敏）", description = "根据用户 ID 获取脱敏后的用户视图信息")
    @GetMapping("/get/vo")
    public BaseResponse<UserVO> getUserVOById(
            @Parameter(description = "用户 ID", required = true) @RequestParam BigInteger id) {
        BaseResponse<User> response = getUserById(id);
        User user = response.getData();
        return ResultUtils.success(userService.getUserVO(user));
    }

    @Operation(summary = "删除用户（管理员）", description = "管理员根据用户 ID 删除指定用户")
    @PostMapping("/delete")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> deleteUser(@RequestBody DeleteRequest deleteRequest) {
        if (deleteRequest == null || deleteRequest.getId() == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        BigInteger id = BigInteger.valueOf(deleteRequest.getId());
        boolean b = userService.removeById(id);
        return ResultUtils.success(b);
    }

    @Operation(summary = "更新用户（管理员）", description = "管理员更新指定用户的用户名、邮箱、角色与状态")
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateUser(@RequestBody UserUpdateRequest userUpdateRequest) {
        if (userUpdateRequest == null || userUpdateRequest.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user = new User();
        BeanUtil.copyProperties(userUpdateRequest, user);
        boolean result = userService.updateById(user);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    @Operation(summary = "分页查询用户列表（管理员）", description = "管理员分页查询用户列表，支持多条件筛选")
    @PostMapping("/list/page/vo")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<UserVO>> listUserVOByPage(@RequestBody UserQueryRequest userQueryRequest) {
        ThrowUtils.throwIf(userQueryRequest == null, ErrorCode.PARAMS_ERROR);
        long pageNum = userQueryRequest.getPageNum();
        long pageSize = userQueryRequest.getPageSize();
        Page<User> userPage = userService.page(Page.of(pageNum, pageSize),
                userService.getQueryWrapper(userQueryRequest));
        Page<UserVO> userVOPage = new Page<>(pageNum, pageSize, userPage.getTotalRow());
        List<UserVO> userVOList = userService.getUserVOList(userPage.getRecords());
        userVOPage.setRecords(userVOList);
        return ResultUtils.success(userVOPage);
    }

    // endregion

}
