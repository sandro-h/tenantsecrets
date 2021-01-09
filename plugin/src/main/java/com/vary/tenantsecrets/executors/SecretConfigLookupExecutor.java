/*
 * Copyright 2019 ThoughtWorks, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.vary.tenantsecrets.executors;

import com.vary.tenantsecrets.crypto.AesEncrypter;
import com.vary.tenantsecrets.crypto.ContextSpecificSecretProvider;
import com.vary.tenantsecrets.crypto.HKDFKeyDeriver;
import com.vary.tenantsecrets.crypto.KeyProvider;
import com.vary.tenantsecrets.models.LookupSecretRequest;
import com.vary.tenantsecrets.models.ResolvedSecret;
import com.github.bdpiparva.plugin.base.executors.secrets.LookupExecutor;
import com.thoughtworks.go.plugin.api.logging.Logger;
import com.thoughtworks.go.plugin.api.response.DefaultGoPluginApiResponse;
import com.thoughtworks.go.plugin.api.response.GoPluginApiResponse;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import static com.github.bdpiparva.plugin.base.GsonTransformer.toJson;

public class SecretConfigLookupExecutor extends LookupExecutor<LookupSecretRequest> {

    private static final Logger LOG = Logger.getLoggerFor(SecretConfigLookupExecutor.class);

    private ContextSpecificSecretProvider secretProvider;

    @Override
    protected GoPluginApiResponse execute(LookupSecretRequest request) {
        String pipelineGroup = request.getConfig().getPipelineGroup();

        ContextSpecificSecretProvider secretProvider;
        try {
            secretProvider = getSecretProvider(request.getConfig().getCipherFile());
        } catch (IOException e) {
            LOG.error("Could not load secret provider for cipher file " + request.getConfig().getCipherFile(),
                    e);
            String message = "Could not decrypt secret. Failed to load the secret provider. Check logs.";
            return new DefaultGoPluginApiResponse(404,
                    "{\"message\": \"" + message + "\"}");
        }

        List<ResolvedSecret> resolvedSecrets = new LinkedList<>();
        for (String secret: request.getKeys()) {
            try {
                String decrypted = secretProvider.decrypt(secret, pipelineGroup);
                resolvedSecrets.add(new ResolvedSecret(secret, decrypted));
            }
            catch (Exception e) {
                LOG.error("Could not decrypt secret " + secret,  e);
                String message = "Could not decrypt secret. Was it generated for the " +
                        "pipeline group defined for this secret config (" + pipelineGroup + ")?";
                return new DefaultGoPluginApiResponse(404,
                        "{\"message\": \"" + message + "\"}");
            }
        }

        return DefaultGoPluginApiResponse.success(toJson(resolvedSecrets));
    }

    @Override
    protected LookupSecretRequest parseRequest(String body) {
        return LookupSecretRequest.fromJSON(body);
    }

    private ContextSpecificSecretProvider getSecretProvider(String cipherFile) throws IOException {
        if (secretProvider == null) {
            secretProvider = new ContextSpecificSecretProvider(
                    new AesEncrypter(),
                    new HKDFKeyDeriver(),
                    KeyProvider.fromFile(new File(cipherFile))
            );
        }
        return secretProvider;
    }
}