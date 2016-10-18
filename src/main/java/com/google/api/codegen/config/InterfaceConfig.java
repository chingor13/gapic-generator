/* Copyright 2016 Google Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.api.codegen.config;

import com.google.api.codegen.CollectionConfigProto;
import com.google.api.codegen.ConfigProto;
import com.google.api.codegen.IamResourceProto;
import com.google.api.codegen.InterfaceConfigProto;
import com.google.api.codegen.MethodConfigProto;
import com.google.api.codegen.RetryCodesDefinitionProto;
import com.google.api.codegen.RetryParamsDefinitionProto;
import com.google.api.gax.core.RetrySettings;
import com.google.api.tools.framework.model.Diag;
import com.google.api.tools.framework.model.DiagCollector;
import com.google.api.tools.framework.model.Field;
import com.google.api.tools.framework.model.Interface;
import com.google.api.tools.framework.model.Method;
import com.google.api.tools.framework.model.Model;
import com.google.api.tools.framework.model.SimpleLocation;
import com.google.api.tools.framework.model.TypeRef;
import com.google.auto.value.AutoValue;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import org.joda.time.Duration;

import io.grpc.Status;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;

import javax.annotation.Nullable;

/**
 * InterfaceConfig represents the code-gen config for an API interface, and includes the
 * configuration for methods and resource names.
 */
@AutoValue
public abstract class InterfaceConfig {
  public abstract List<MethodConfig> getMethodConfigs();

  @Nullable
  public abstract SmokeTestConfig getSmokeTestConfig();

  abstract ImmutableMap<String, CollectionConfig> collectionConfigs();

  abstract ImmutableMap<String, MethodConfig> getMethodConfigMap();

  public abstract ImmutableMap<String, ImmutableSet<Status.Code>> getRetryCodesDefinition();

  public abstract ImmutableMap<String, RetrySettings> getRetrySettingsDefinition();

  public abstract ImmutableList<Field> getIamResources();

  /**
   * Creates an instance of InterfaceConfig based on ConfigProto, linking up method configurations
   * with specified methods in methodConfigMap. On errors, null will be returned, and diagnostics
   * are reported to the model.
   */
  @Nullable
  public static InterfaceConfig createInterfaceConfig(
      DiagCollector diagCollector,
      String language,
      InterfaceConfigProto interfaceConfigProto,
      Interface iface) {
    ImmutableMap<String, CollectionConfig> collectionConfigs =
        createCollectionConfigs(diagCollector, interfaceConfigProto);

    ImmutableMap<String, ImmutableSet<Status.Code>> retryCodesDefinition =
        createRetryCodesDefinition(diagCollector, interfaceConfigProto);
    ImmutableMap<String, RetrySettings> retrySettingsDefinition =
        createRetrySettingsDefinition(diagCollector, interfaceConfigProto);

    List<MethodConfig> methodConfigs = null;
    ImmutableMap<String, MethodConfig> methodConfigMap = null;
    if (retryCodesDefinition != null && retrySettingsDefinition != null) {
      methodConfigMap =
          createMethodConfigMap(
              diagCollector,
              language,
              interfaceConfigProto,
              iface,
              retryCodesDefinition.keySet(),
              retrySettingsDefinition.keySet());
      methodConfigs = createMethodConfigs(methodConfigMap, interfaceConfigProto);
    }

    SmokeTestConfig smokeTestConfig =
        createSmokeTestConfig(diagCollector, iface, interfaceConfigProto);

    ImmutableList<Field> iamResources =
        createIamResources(
            iface.getModel(), interfaceConfigProto.getExperimentalFeatures().getIamResourcesList());

    if (diagCollector.hasErrors()) {
      return null;
    } else {
      return new AutoValue_InterfaceConfig(
          methodConfigs,
          smokeTestConfig,
          collectionConfigs,
          methodConfigMap,
          retryCodesDefinition,
          retrySettingsDefinition,
          iamResources);
    }
  }

  private static SmokeTestConfig createSmokeTestConfig(
      DiagCollector diagCollector, Interface iface, InterfaceConfigProto interfaceConfigProto) {
    if (interfaceConfigProto.hasSmokeTest()) {
      return SmokeTestConfig.createSmokeTestConfig(
          iface, interfaceConfigProto.getSmokeTest(), diagCollector);
    } else {
      return null;
    }
  }

  private static ImmutableMap<String, CollectionConfig> createCollectionConfigs(
      DiagCollector diagCollector, InterfaceConfigProto interfaceConfigProto) {
    ImmutableMap.Builder<String, CollectionConfig> collectionConfigsBuilder =
        ImmutableMap.builder();

    for (CollectionConfigProto collectionConfigProto : interfaceConfigProto.getCollectionsList()) {
      CollectionConfig collectionConfig =
          CollectionConfig.createCollection(diagCollector, collectionConfigProto);
      if (collectionConfig == null) {
        continue;
      }
      collectionConfigsBuilder.put(collectionConfig.getEntityName(), collectionConfig);
    }

    if (diagCollector.getErrorCount() > 0) {
      return null;
    } else {
      return collectionConfigsBuilder.build();
    }
  }

  private static ImmutableMap<String, ImmutableSet<Status.Code>> createRetryCodesDefinition(
      DiagCollector diagCollector, InterfaceConfigProto interfaceConfigProto) {
    ImmutableMap.Builder<String, ImmutableSet<Status.Code>> builder =
        ImmutableMap.<String, ImmutableSet<Status.Code>>builder();
    for (RetryCodesDefinitionProto retryDef : interfaceConfigProto.getRetryCodesDefList()) {
      EnumSet<Status.Code> codes = EnumSet.noneOf(Status.Code.class);
      for (String codeText : retryDef.getRetryCodesList()) {
        try {
          codes.add(Status.Code.valueOf(codeText));
        } catch (IllegalArgumentException e) {
          diagCollector.addDiag(
              Diag.error(
                  SimpleLocation.TOPLEVEL,
                  "status code not found: '%s' (in interface %s)",
                  codeText,
                  interfaceConfigProto.getName()));
        }
      }
      builder.put(retryDef.getName(), Sets.immutableEnumSet(codes));
    }
    if (diagCollector.getErrorCount() > 0) {
      return null;
    }
    return builder.build();
  }

  private static ImmutableMap<String, RetrySettings> createRetrySettingsDefinition(
      DiagCollector diagCollector, InterfaceConfigProto interfaceConfigProto) {
    ImmutableMap.Builder<String, RetrySettings> builder =
        ImmutableMap.<String, RetrySettings>builder();
    for (RetryParamsDefinitionProto retryDef : interfaceConfigProto.getRetryParamsDefList()) {
      try {
        RetrySettings settings =
            RetrySettings.newBuilder()
                .setInitialRetryDelay(Duration.millis(retryDef.getInitialRetryDelayMillis()))
                .setRetryDelayMultiplier(retryDef.getRetryDelayMultiplier())
                .setMaxRetryDelay(Duration.millis(retryDef.getMaxRetryDelayMillis()))
                .setInitialRpcTimeout(Duration.millis(retryDef.getInitialRpcTimeoutMillis()))
                .setRpcTimeoutMultiplier(retryDef.getRpcTimeoutMultiplier())
                .setMaxRpcTimeout(Duration.millis(retryDef.getMaxRpcTimeoutMillis()))
                .setTotalTimeout(Duration.millis(retryDef.getTotalTimeoutMillis()))
                .build();
        builder.put(retryDef.getName(), settings);
      } catch (IllegalStateException | NullPointerException e) {
        diagCollector.addDiag(
            Diag.error(
                SimpleLocation.TOPLEVEL,
                "error while creating retry params: %s (in interface %s)",
                e,
                interfaceConfigProto.getName()));
      }
    }
    if (diagCollector.getErrorCount() > 0) {
      return null;
    }
    return builder.build();
  }

  private static ImmutableMap<String, MethodConfig> createMethodConfigMap(
      DiagCollector diagCollector,
      String language,
      InterfaceConfigProto interfaceConfigProto,
      Interface iface,
      ImmutableSet<String> retryCodesConfigNames,
      ImmutableSet<String> retryParamsConfigNames) {
    ImmutableMap.Builder<String, MethodConfig> methodConfigMapBuilder = ImmutableMap.builder();

    for (MethodConfigProto methodConfigProto : interfaceConfigProto.getMethodsList()) {
      Interface targetInterface =
          getTargetInterface(iface, methodConfigProto.getRerouteToGrpcInterface());
      Method method = targetInterface.lookupMethod(methodConfigProto.getName());
      if (method == null) {
        diagCollector.addDiag(
            Diag.error(
                SimpleLocation.TOPLEVEL, "method not found: %s", methodConfigProto.getName()));
        continue;
      }
      MethodConfig methodConfig =
          MethodConfig.createMethodConfig(
              diagCollector,
              language,
              methodConfigProto,
              method,
              retryCodesConfigNames,
              retryParamsConfigNames);
      if (methodConfig == null) {
        continue;
      }
      methodConfigMapBuilder.put(methodConfigProto.getName(), methodConfig);
    }

    if (diagCollector.getErrorCount() > 0) {
      return null;
    } else {
      return methodConfigMapBuilder.build();
    }
  }

  private static List<MethodConfig> createMethodConfigs(
      ImmutableMap<String, MethodConfig> methodConfigMap,
      InterfaceConfigProto interfaceConfigProto) {
    List<MethodConfig> methodConfigs = new ArrayList<>();
    for (MethodConfigProto methodConfigProto : interfaceConfigProto.getMethodsList()) {
      methodConfigs.add(methodConfigMap.get(methodConfigProto.getName()));
    }
    return methodConfigs;
  }

  public CollectionConfig getCollectionConfig(String entityName) {
    return collectionConfigs().get(entityName);
  }

  /**
   * Returns the list of CollectionConfigs.
   */
  public Collection<CollectionConfig> getCollectionConfigs() {
    return collectionConfigs().values();
  }

  /**
   * Returns the MethodConfig for the given method.
   */
  public MethodConfig getMethodConfig(Method method) {
    MethodConfig methodConfig = getMethodConfigMap().get(method.getSimpleName());
    if (methodConfig == null) {
      throw new IllegalArgumentException(
          "no method config for method '" + method.getFullName() + "'");
    }
    return methodConfig;
  }

  /**
   * If rerouteToGrpcInterface is set, then looks up that interface and returns it, otherwise
   * returns the value of defaultInterface.
   */
  public static Interface getTargetInterface(
      Interface defaultInterface, String rerouteToGrpcInterface) {
    Interface targetInterface = defaultInterface;
    if (!Strings.isNullOrEmpty(rerouteToGrpcInterface)) {
      targetInterface =
          defaultInterface.getModel().getSymbolTable().lookupInterface(rerouteToGrpcInterface);
      if (targetInterface == null) {
        throw new IllegalArgumentException(
            "reroute_to_grpc_interface not found: " + rerouteToGrpcInterface);
      }
    }
    return targetInterface;
  }

  /** Creates a list of fields that can be turned into IAM resources */
  private static ImmutableList<Field> createIamResources(
      Model model, List<IamResourceProto> resources) {
    ImmutableList.Builder<Field> fields = ImmutableList.builder();
    for (IamResourceProto resource : resources) {
      TypeRef type = model.getSymbolTable().lookupType(resource.getType());
      if (type == null) {
        throw new IllegalArgumentException("type not found: " + resource.getType());
      }
      if (!type.isMessage()) {
        throw new IllegalArgumentException("type must be a message: " + type);
      }
      Field field = type.getMessageType().lookupField(resource.getField());
      if (field == null) {
        throw new IllegalArgumentException(
            String.format(
                "type %s does not have field %s", resource.getType(), resource.getField()));
      }
      fields.add(field);
    }
    return fields.build();
  }
}