package com.vary.tenantsecrets.tenantsecrets;

import com.vary.tenantsecrets.crypto.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpServerErrorException;

import java.io.File;
import java.io.IOException;

@RestController
@RequestMapping("/secrets")
public class SecretsController {

	private final File cipherFile;
	private ContextSpecificSecretProvider secretProvider;

	public SecretsController(@Value("${tenantsecrets.cipherFile}") File cipherFile) {
		this.cipherFile = cipherFile;
	}

	@PostMapping("/{pipelineGroup}")
	public ResponseEntity<String> encrypt(@PathVariable("pipelineGroup") String pipelineGroup,
										 @RequestBody String plainText) {
		try {
			String secret = getSecretProvider().encrypt(plainText, pipelineGroup);
			return ResponseEntity.status(HttpStatus.CREATED).body(secret);
		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error generating secret.");
		}
	}

	private ContextSpecificSecretProvider getSecretProvider() throws IOException {
		if (secretProvider == null) {
			System.out.println(cipherFile.getAbsolutePath());
			secretProvider = new ContextSpecificSecretProvider(
					new AesEncrypter(),
					new HKDFKeyDeriver(),
					KeyProvider.fromFile(cipherFile)
			);
		}
		return secretProvider;
	}
}
