package com.boostercloud.integration.roles;

import com.boostercloud.framework.core.decorators.Role;
import com.boostercloud.framework.core.decorators.RoleAuth;

/** Role {@code SuperUserNoConfirmation} ({@code src/roles.ts}): sign-up by email or phone, confirmed automatically. */
@Role(auth = @RoleAuth(signUpMethods = {"email", "phone"}, skipConfirmation = true))
public class SuperUserNoConfirmation {}
