/**
 * Cross-cutting building blocks shared by every feature module: error handling,
 * pagination, auditing base entity (ARCHITECTURE.md §5.2).
 */
@ApplicationModule(type = ApplicationModule.Type.OPEN)
package com.familybudget.common;

import org.springframework.modulith.ApplicationModule;
