/*
 * Copyright (C) 2019~2020 dinstone<dinstone@163.com>
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
package com.dinstone.agate.gateway;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.dinstone.agate.gateway.options.AppOptions;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.MessageCodec;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;

public class EventbusTest {

	private static final Logger LOG = LoggerFactory.getLogger(EventbusTest.class);

	public static void main(String[] args) {

		JsonObject json = getJsonFromFile("src/main/resources/config.json");

		Vertx vertx = Vertx.vertx();

		vertx.eventBus().registerDefaultCodec(AppOptions.class, new MessageCodec<AppOptions, AppOptions>() {

			@Override
			public void encodeToWire(Buffer buffer, AppOptions s) {
				// TODO Auto-generated method stub
			}

			@Override
			public AppOptions decodeFromWire(int pos, Buffer buffer) {
				// TODO Auto-generated method stub
				return null;
			}

			@Override
			public AppOptions transform(AppOptions s) {
				// TODO Auto-generated method stub
				return null;
			}

			@Override
			public String name() {
				return "app";
			}

			@Override
			public byte systemCodecID() {
				return -1;
			}

		});
		vertx.eventBus().send("test", new AppOptions());

		System.out.println("ok");
	}

	static JsonObject getJsonFromFile(String jsonFile) {
		if (jsonFile != null) {
			try (Scanner scanner = new Scanner(new File(jsonFile), "UTF-8").useDelimiter("\\A")) {
				String sconf = scanner.next();
				try {
					return new JsonObject(sconf);
				} catch (DecodeException e) {
					LOG.error("Configuration file " + sconf + " does not contain a valid JSON object");
				}
			} catch (FileNotFoundException e) {
				LOG.error("unkown know file " + jsonFile, e);
			}
		}

		return null;
	}

}
