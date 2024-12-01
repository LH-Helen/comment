package com.hmdp.common.context;

import com.hmdp.dto.UserDTO;

public class BaseContext {
    private static final ThreadLocal<UserDTO> threadLocal = new ThreadLocal<>();

    public static void setCurrentUser(UserDTO user){
        threadLocal.set(user);
    }

    public static UserDTO getCurrentUser(){
        return threadLocal.get();
    }

    public static void removeCurrentUser(){
        threadLocal.remove();
    }
}
