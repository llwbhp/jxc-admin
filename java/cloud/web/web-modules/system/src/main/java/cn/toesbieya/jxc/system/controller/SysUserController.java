package cn.toesbieya.jxc.system.controller;

import cn.toesbieya.jxc.common.model.entity.RecUserAction;
import cn.toesbieya.jxc.common.model.entity.SysUser;
import cn.toesbieya.jxc.common.model.vo.Result;
import cn.toesbieya.jxc.system.model.vo.UserSearch;
import cn.toesbieya.jxc.system.service.SysUserService;
import cn.toesbieya.jxc.web.common.utils.ThreadUtil;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("user")
public class SysUserController {
    @Resource
    private SysUserService userService;

    @PostMapping("search")
    public Result search(@RequestBody UserSearch vo) {
        return Result.success(userService.search(vo));
    }

    @PostMapping("add")
    public Result add(@RequestBody SysUser user) {
        String errMsg = validateUserCreateParam(user);
        if (errMsg != null) {
            return Result.fail(errMsg);
        }
        return userService.add(user);
    }

    @PostMapping("update")
    public Result update(@RequestBody SysUser user) {
        String errMsg = validateUserUpdateParam(user);
        if (errMsg != null) {
            return Result.fail(errMsg);
        }
        return userService.update(user);
    }

    @PostMapping("del")
    public Result del(@RequestBody SysUser user) {
        if (user.getId() == null) {
            return Result.fail("删除失败");
        }
        return userService.del(user);
    }

    @PostMapping("kick")
    public Result kick(@RequestBody List<SysUser> users) {
        if (users == null || users.isEmpty()) {
            return Result.fail("参数错误");
        }
        RecUserAction action = ThreadUtil.getAction();
        List<String> names = users.stream().map(SysUser::getName).collect(Collectors.toList());
        action.setAction("踢出用户：【" + String.join(",", names) + "】");

        return userService.kick(users);
    }

    @PostMapping("resetPwd")
    public Result resetPwd(@RequestBody SysUser user) {
        if (user.getId() == null || StringUtils.isEmpty(user.getName())) {
            return Result.fail("参数错误");
        }
        return userService.resetPwd(user);
    }

    private String validateUserCreateParam(SysUser user) {
        if (user.getId() != null) return "创建失败，参数错误";
        if (StringUtils.isEmpty(user.getName())) return "创建失败，用户名称不能为空";
        if (user.getRole() == null) return "创建失败，用户角色不能为空";
        return null;
    }

    private String validateUserUpdateParam(SysUser user) {
        if (user.getId() == null) return "修改失败，参数错误";
        if (StringUtils.isEmpty(user.getName())) return "修改失败，用户名称不能为空";
        if (user.getRole() == null) return "修改失败，用户角色不能为空";
        if (user.getStatus() == null) return "修改失败，用户状态不能为空";
        return null;
    }
}
