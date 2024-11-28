package com.hmdp.service;

import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * <p>
 *  服务类
 * </p>
 */
public interface IFollowService extends IService<Follow> {

    void follow(Long followUserId, Boolean isFollow);

    boolean isFollow(Long followUserId);

    List<UserDTO> followCommons(Long id);
}
