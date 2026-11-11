package com.boostercloud.integration.roles;

import com.boostercloud.framework.core.decorators.Role;
import com.boostercloud.framework.core.decorators.RoleAuth;

/** Role {@code UserWithEmail} ({@code src/roles.ts}): sign-up by email, with confirmation. */
@Role(auth = @RoleAuth(signUpMethods = {"email"}, skipConfirmation = false))
public class UserWithEmail {}
