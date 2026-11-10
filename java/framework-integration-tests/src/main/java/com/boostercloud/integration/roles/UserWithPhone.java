package com.boostercloud.integration.roles;

import com.boostercloud.framework.core.decorators.Role;
import com.boostercloud.framework.core.decorators.RoleAuth;

/** Role {@code UserWithPhone} ({@code src/roles.ts}): sign-up by phone. */
@Role(auth = @RoleAuth(signUpMethods = {"phone"}))
public class UserWithPhone {}
