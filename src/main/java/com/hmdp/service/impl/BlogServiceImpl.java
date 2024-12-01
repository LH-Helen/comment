package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.common.constant.MessageConstants;
import com.hmdp.common.exception.BlogException;
import com.hmdp.common.result.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.common.constant.RedisConstants;
import com.hmdp.common.constant.SystemConstants;
import com.hmdp.common.context.BaseContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 服务实现类
 * </p>
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Autowired
    private IUserService userService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private IFollowService followService;

    @Override
    public List<Blog> queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(this::queryBlogUser);
        return records;
    }

    @Override
    public Blog queryBlogById(Long id) {
        Blog blog = getById(id);
        if (blog == null) {
            throw new BlogException(MessageConstants.BLOG_NOT_FOUND);
        }
        queryBlogUser(blog);
        return blog;
    }

    private Boolean isBlogLiked(Long id) {
        // 登录用户
        UserDTO user = BaseContext.getCurrentUser();
        if (user == null) {
            return false;
        }
        Long userId = user.getId();
        // 判断用户是否点赞
        String key = RedisConstants.BLOG_LIKED_KEY + id;
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        return score != null;
    }

    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
        blog.setIsLike(isBlogLiked(blog.getId()));
    }

    @Override
    public void likeBlog(Long id) {
        Long userId = BaseContext.getCurrentUser().getId();
        String key = RedisConstants.BLOG_LIKED_KEY + id;
        Boolean isMember = isBlogLiked(id);

        if (BooleanUtil.isFalse(isMember)) {
            // 未点赞：
            // 数据库点赞数+1
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
            // 保存用户到redis的set集合
            if (isSuccess) {
                stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
            }

        } else {
            // 点过赞
            // 数据库点赞数-1
            boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
            // 把用户从redis的set集合中移除
            if (isSuccess) {
                stringRedisTemplate.opsForZSet().remove(key, userId.toString());
            }
        }
    }

    @Override
    public List<UserDTO> queryBlogLikes(Long id) {
        // 查询top5的点赞用户
        String key = RedisConstants.BLOG_LIKED_KEY + id;
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if (top5 == null || top5.isEmpty()) {
            return Collections.emptyList();
        }
        // 解析用户id查询用户id
        List<Long> userIds = top5.stream().map(Long::valueOf).collect(Collectors.toList());
        String idStr = StrUtil.join(",", userIds);
        // 根据用户id查询用户
//        List<UserDTO> userDTOs = userService.listByIds(userIds)
        List<UserDTO> userDTOs = userService.query()
                .in("id", userIds).last("ORDER BY FIELD(id, " + idStr + ")").list()
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());

        return userDTOs;
    }

    @Override
    public void saveBlog(Blog blog) {
        // 获取登录用户
        UserDTO user = BaseContext.getCurrentUser();
        blog.setUserId(user.getId());
        // 保存探店博文
        boolean isSuccess = save(blog);
        if (!isSuccess) {
            throw new BlogException(MessageConstants.BLOG_SAVE_FAILED);
        }
        // 查询笔记作者的所有粉丝，select
        List<Follow> follows = followService.query().eq("follow_user_id", user.getId()).list();
        // 推送笔记id给所有粉丝
        for (Follow follow : follows) {
            Long userId = follow.getUserId();
            // 推送
            String key = RedisConstants.FEED_KEY + userId;
            stringRedisTemplate.opsForZSet().add(key, blog.getId().toString(), System.currentTimeMillis());
        }
    }

    @Override
    public ScrollResult queryBlogOfFollow(Long max, Integer offset) {
        // 获取当前用户
        Long userId = BaseContext.getCurrentUser().getId();
        // 查询收件箱
        String key = RedisConstants.FEED_KEY + userId;
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet().
                reverseRangeByScoreWithScores(key, 0, max, offset, 2);

        if(typedTuples == null || typedTuples.isEmpty()){
            return new ScrollResult();
        }
        // 解析数据：blogId、score（时间戳）、offset
        List<Long> ids = new ArrayList<>(typedTuples.size());
        long minTime = 0;
        int offsetCount = 1;
        for (ZSetOperations.TypedTuple<String> tuple : typedTuples){
            // 获取id
            ids.add(Long.valueOf(tuple.getValue()));
            // 获取分数（时间戳）
            long time = tuple.getScore().longValue();
            if(minTime == time){
                offsetCount++;
            }else{
                minTime = time;
                offsetCount = 1;
            }
        }
        String idStr = StrUtil.join(",", ids);
        List<Blog> blogs = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();
        // blog显示相关信息
        blogs.forEach(this::queryBlogUser);

        ScrollResult scrollResult = new ScrollResult();
        scrollResult.setList(blogs);
        scrollResult.setOffset(offsetCount);
        scrollResult.setMinTime(minTime);
        // 根据id查询blog
        return scrollResult;
    }
}
