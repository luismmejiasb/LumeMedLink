package com.luismejias.lumemedlink.android

import com.luismejias.lumemedlink.app.AppEnvironment

/** A release build runs on the script until the platform is deployed (ADR-0058 point 5, ADR-0062): no local backend. */
internal fun buildEnvironment(): AppEnvironment = AppEnvironment.Script
