/*******************************************************************************
 * Copyright (c) 2020 Bosch.IO GmbH and others.
 * 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * and Eclipse Distribution License v1.0 which accompany this distribution.
 * 
 * The Eclipse Public License is available at
 *    http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at
 *    http://www.eclipse.org/org/documents/edl-v10.html.
 * 
 * Contributors:
 *    Bosch.IO GmbH - initial creation
 ******************************************************************************/
package org.eclipse.californium.cli;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Random;

import javax.crypto.SecretKey;

import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.coap.CoAP.Type;
import org.eclipse.californium.core.config.CoapConfig;
import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.eclipse.californium.elements.util.StringUtil;

import picocli.CommandLine;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.INegatableOptionTransformer;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;

/**
 * Client command line configuration.
 * 
 * @since 2.3
 */
public class ClientConfig extends ClientBaseConfig {

	/**
	 * Content type.
	 */
	@ArgGroup(exclusive = true)
	public ContentType contentType;

	public static class ContentType {

		/**
		 * Use JSON.
		 */
		@Option(names = "--json", description = "use json payload.")
		public boolean json;

		/**
		 * Use CBOR.
		 */
		@Option(names = "--cbor", description = "use cbor payload.")
		public boolean cbor;

		/**
		 * Use XML.
		 */
		@Option(names = "--xml", description = "use xml payload.")
		public boolean xml;

		/**
		 * Use plain-text.
		 */
		@Option(names = "--text", description = "use plain-text payload.")
		public boolean text;

		/**
		 * Use octet-stream.
		 */
		@Option(names = "--octets", description = "use octet stream payload.")
		public boolean octets;

		/**
		 * Use type from {@link MediaTypeRegistry#parse(String)}.
		 */
		@Option(names = "--ctype", paramLabel = "TYPE", description = "use content type for payload.")
		public String type;

		/**
		 * Numerical value of content type.
		 * 
		 * @see MediaTypeRegistry
		 */
		public int contentType;

		public void defaults() {
			if (type != null) {
				contentType = MediaTypeRegistry.parse(type);
			} else if (text) {
				contentType = MediaTypeRegistry.TEXT_PLAIN;
			} else if (json) {
				contentType = MediaTypeRegistry.APPLICATION_JSON;
			} else if (cbor) {
				contentType = MediaTypeRegistry.APPLICATION_CBOR;
			} else if (xml) {
				contentType = MediaTypeRegistry.APPLICATION_XML;
			} else if (octets) {
				contentType = MediaTypeRegistry.APPLICATION_OCTET_STREAM;
			}
		}
	}

	/**
	 * Payload.
	 */
	@ArgGroup(exclusive = true)
	public Payload payload;

	public static class Payload {

		/**
		 * Payload as text (utf8).
		 */
		@Option(names = "--payload", description = "payload, utf8")
		public String text;

		/**
		 * Payload hexadecimal.
		 */
		@Option(names = "--payloadhex", description = "payload, hexadecimal")
		public String hex;

		/**
		 * Payload base64.
		 */
		@Option(names = "--payload64", description = "payload, base64")
		public String base64;

		/**
		 * Random payload size.
		 * 
		 * @since 3.0
		 */
		@Option(names = "--payload-random", description = "random payload size")
		public Integer size;

		/**
		 * Payload from file.
		 * 
		 * @since 2.4
		 */
		@Option(names = "--payload-file", description = "payload from file")
		public String filename;

		/**
		 * Payload in bytes.
		 * 
		 * (Moved from {@link ClientConfig}.)
		 * 
		 * @since 3.0
		 */
		public byte[] payloadBytes;

		/**
		 * Setup payload defaults.
		 * 
		 * (Moved from {@link ClientConfig}.)
		 * 
		 * @param max maximum supported payload size
		 * @since 3.0
		 */
		public void defaults(int max) {
			if (payloadBytes == null) {
				if (text != null) {
					payloadBytes = text.getBytes();
				} else if (hex != null) {
					payloadBytes = StringUtil.hex2ByteArray(hex);
				} else if (base64 != null) {
					payloadBytes = StringUtil.base64ToByteArray(base64);
				} else if (size != null) {
					if (size <= max) {
						Random rand = new Random();
						payloadBytes = new byte[(int) size];
						for (int index=0; index < size; ++index) {
							payloadBytes[index] = (byte)(' ' + rand.nextInt(127 - ' '));
						}
					} else {
						LOGGER.error("Random payload with {} bytes is too large! (Maximum {} bytes.)", size, max);
					}
				} else if (filename != null) {
					File file = new File(filename);
					if (file.canRead()) {
						long length = file.length();
						if (length <= max) {
							payloadBytes = new byte[(int)length];
							InputStream in = null;
							try {
								in = new FileInputStream(file);
								int len = in.read(payloadBytes);
								if (len != length) {
									LOGGER.error("file {} with {} bytes, read {} bytes!", file, length, len);
								}
							} catch (FileNotFoundException e) {
								LOGGER.error("Missing file {}", file, e);
							} catch (IOException e) {
								LOGGER.error("Error reading file {}", file, e);
							} finally {
								if (in != null) {
									try {
										in.close();
									} catch (IOException e) {
										LOGGER.error("Error closing file {}", file, e);
									}
								}
							}
						} else {
							LOGGER.error("file {} with {} bytes is too large! (Maximum {} bytes.)", file, length, max);
						}
					} else {
						LOGGER.error("Can not read file {} ({})", file, file.getAbsolutePath());
					}
				}
			}
		}
	}

	/**
	 * Apply {@link String#format(String, Object...)} to payload. The used
	 * parameter depends on the client implementation.
	 * 
	 * @since 2.4
	 */
	@Option(names = "--payload-format", description = "apply format to payload.")
	public boolean payloadFormat;

	/**
	 * Message type.
	 * 
	 * @since 2.5
	 */
	@ArgGroup(exclusive = true)
	public MessageType messageType;

	public static class MessageType {

		/**
		 * Request type. {@code true} for {@link Type#CON}.
		 */
		@Option(names = "--con", description = "send request confirmed.")
		public boolean con;
		/**
		 * Request type. {@code true} for {@link Type#NON}.
		 */
		@Option(names = "--non", description = "send request non-confirmed.")
		public boolean non;
	}

	/**
	 * Request method.
	 */
	@Option(names = { "-m", "--method" }, description = "use method. GET|PUT|POST|DELETE|FETCH|PATCH|IPATCH.")
	public CoAP.Code method;

	@Override
	public void register(CommandLine cmd) {
		super.register(cmd);
		cmd.setNegatableOptionTransformer(messageTypeTransformer);
	}

	@Override
	public void defaults() {
		super.defaults();
		if (contentType != null) {
			contentType.defaults();
		}
		if (payload != null) {
			int max = configuration.get(CoapConfig.MAX_RESOURCE_BODY_SIZE);
			payload.defaults(max);
		}
	}

	@Override
	public ClientConfig create() {
		return (ClientConfig) super.create();
	}

	@Override
	public ClientConfig create(String id, SecretKey secret) {
		return (ClientConfig) super.create(id, secret);
	}

	@Override
	public ClientConfig create(PrivateKey privateKey, PublicKey publicKey) {
		return (ClientConfig) super.create(privateKey, publicKey);
	}

	/**
	 * Negatable transformer. Transforms "--con" to "-non".
	 */
	public static INegatableOptionTransformer messageTypeTransformer = new INegatableOptionTransformer() {

		private INegatableOptionTransformer delegate = CommandLine.RegexTransformer.createDefault();

		@Override
		public String makeNegative(String optionName, CommandSpec cmd) {
			if ("--con".equals(optionName)) {
				return "--non";
			} else {
				return delegate.makeNegative(optionName, cmd);
			}
		}

		@Override
		public String makeSynopsis(String optionName, CommandSpec cmd) {
			if ("--con".equals(optionName)) {
				return "(--con|--non)";
			} else {
				return delegate.makeSynopsis(optionName, cmd);
			}
		}

	};
}
