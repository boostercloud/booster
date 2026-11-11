package com.boostercloud.framework.core.integration.app.roles;

import com.boostercloud.framework.core.decorators.Role;
import com.boostercloud.framework.core.decorators.RoleAuth;

/** Store administrators. */
@Role(auth = @RoleAuth(signUpMethods = {"email"}, skipConfirmation = true))
public class Admin {}
