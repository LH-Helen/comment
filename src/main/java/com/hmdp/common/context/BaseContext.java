package com.hmdp.common.context;

import com.hmdp.dto.UserDTO;

public class BaseContext {
    private static final ThreadLocal<UserDTO> threadLocal = new ThreadLocal<>();

    public static void setCurrentId(UserDTO user){
        threadLocal.set(user);
    }

    public static UserDTO getCurrentId(){
        return threadLocal.get();
    }

    public static void removeCurrentId(){
        threadLocal.remove();
    }
}
