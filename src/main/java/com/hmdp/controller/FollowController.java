package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.service.IFollowService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * <p>
 *  前端控制器
 * </p>
 */
@RestController
@RequestMapping("/follow")
public class FollowController {

    @Autowired
    private IFollowService followService;

    @PutMapping("/{id}/{isFollow}")
    public Result follewow(@PathVariable Long id, @PathVariable Boolean isFollow){
        followService.follow(id, isFollow);
        return Result.ok();
    }

    @GetMapping("/or/not/{id}")
    public Result follewow(@PathVariable Long id){
        boolean isFollow = followService.isFollow(id);
        return Result.ok(isFollow);
    }

    @GetMapping("/common/{id}")
    public Result followCommons(@PathVariable Long id){
        List<UserDTO> users = followService.followCommons(id);
        return Result.ok(users);
    }
}
