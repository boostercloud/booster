package com.boostercloud.integration.roles;

import com.boostercloud.framework.core.decorators.Role;
import com.boostercloud.framework.core.decorators.RoleAuth;

/** Role {@code SuperUser} ({@code src/roles.ts}): sign-up by email or phone, with confirmation. */
@Role(auth = @RoleAuth(signUpMethods = {"email", "phone"}, skipConfirmation = false))
public class SuperUser {}
