package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;

import java.util.List;

/**
 * <p>
 * 服务类
 * </p>
 */
public interface IBlogService extends IService<Blog> {

    List<Blog> queryHotBlog(Integer current);


    Blog queryBlogById(Long id);


    void likeBlog(Long id);

    List<UserDTO> queryBlogLikes(Long id);
}
