package com.boostercloud.integration.roles;

import com.boostercloud.framework.core.decorators.Role;
import com.boostercloud.framework.core.decorators.RoleAuth;

/** Role {@code Admin} ({@code src/roles.ts}): no sign-up methods. */
@Role(auth = @RoleAuth(signUpMethods = {}))
public class Admin {}
