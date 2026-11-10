package com.example.store.roles;

import com.boostercloud.framework.core.decorators.Role;
import com.boostercloud.framework.core.decorators.RoleAuth;

@Role(auth = @RoleAuth(signUpMethods = {"email"}))
public class Admin {}
