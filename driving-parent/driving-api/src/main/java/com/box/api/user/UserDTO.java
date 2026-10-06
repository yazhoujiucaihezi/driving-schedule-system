package com.box.api.user;

import lombok.Data;

@Data
public class UserDTO {
    private Long id;
    private String username;
    private String password;   // auth 验密码要用
    private String role;
    private Long relatedId;
    private String status;
}