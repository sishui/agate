/*
 * Copyright (C) ${year} dinstone<dinstone@163.com>
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
package com.dinstone.agate.gateway.verticle;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.dinstone.agate.gateway.context.AddressConstant;
import com.dinstone.agate.gateway.context.AgateVerticleFactory;
import com.dinstone.agate.gateway.context.ApplicationContext;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.consul.KeyValue;
import io.vertx.ext.consul.KeyValueList;
import io.vertx.ext.consul.Watch;
import io.vertx.ext.consul.WatchResult;

/**
 * launch the verticles and init gateway context.
 * 
 * @author dinstone
 *
 */
public class LaunchVerticle extends AbstractVerticle {

	private static final Logger LOG = LoggerFactory.getLogger(LaunchVerticle.class);

	private ApplicationContext applicationContext;
	private Watch<KeyValueList> appWatch;

	@Override
	public void stop() throws Exception {
		if (appWatch != null) {
			appWatch.stop();
		}
		if (applicationContext != null) {
			applicationContext.destroy();
		}
	}

	@Override
	public void start(Promise<Void> startPromise) throws Exception {
		// init application context
		applicationContext = new ApplicationContext(config());

		// regist verticle factory
		vertx.registerVerticleFactory(new AgateVerticleFactory(applicationContext));

		// system verticle
		DeploymentOptions svdOptions = new DeploymentOptions().setConfig(config()).setInstances(1);
		Future<String> svfuture = deploy(AgateVerticleFactory.appendPrefix(SystemVerticle.class), svdOptions);

		// deploy verticle
		DeploymentOptions dvdOptions = new DeploymentOptions().setConfig(config()).setInstances(1);
		Future<String> dvFuture = deploy(AgateVerticleFactory.appendPrefix(DeployVerticle.class), dvdOptions);

		CompositeFuture.all(svfuture, dvFuture).compose(f -> manage()).compose(cf -> load()).setHandler(startPromise);
	}

	private Future<String> manage() {
		DeploymentOptions mvdOptions = new DeploymentOptions().setConfig(config()).setInstances(1);
		return deploy(AgateVerticleFactory.appendPrefix(ManageVerticle.class), mvdOptions);
	}

	/**
	 * load api config and deploy
	 * 
	 * @return
	 */
	private Future<Void> load() {
		return Future.future(p -> {
			appWatch = Watch.keyPrefix("agate/apps/" + applicationContext.getClusterId(), vertx,
					applicationContext.getConsulClientOptions()).setHandler(ar -> {
						try {
							watchEventHandle(ar);
						} catch (Exception e) {
							LOG.warn("handle app watch event error", e);
						}
					}).start();
			p.complete();
		});
	}

	private void watchEventHandle(WatchResult<KeyValueList> ar) {
		if (!ar.succeeded()) {
			LOG.warn("app watch event error", ar.cause());
			return;
		}

		Map<String, KeyValue> pkvMap = new HashMap<String, KeyValue>();
		if (ar.prevResult() != null && ar.prevResult().getList() != null) {
			ar.prevResult().getList().forEach(kv -> pkvMap.put(kv.getKey(), kv));
		}

		Map<String, KeyValue> nkvMap = new HashMap<String, KeyValue>();
		if (ar.nextResult() != null && ar.nextResult().getList() != null) {
			ar.nextResult().getList().forEach(kv -> nkvMap.put(kv.getKey(), kv));
		}

		// create: next have and prev not;
		// update: next have and prev have, modify index not equal
		List<KeyValue> cList = new LinkedList<KeyValue>();
		List<KeyValue> uList = new LinkedList<KeyValue>();
		nkvMap.forEach((k, nkv) -> {
			KeyValue pkv = pkvMap.get(k);
			if (pkv == null) {
				cList.add(nkv);
			} else if (pkv.getModifyIndex() != nkv.getModifyIndex()) {
				uList.add(nkv);
			}
		});

		// delete: prev have and next not;
		List<KeyValue> dList = new LinkedList<KeyValue>();
		pkvMap.forEach((k, pkv) -> {
			if (!nkvMap.containsKey(k)) {
				dList.add(pkv);
			}
		});

		uList.forEach(kv -> {
			try {
				JsonObject message = new JsonObject(kv.getValue());
				vertx.eventBus().send(AddressConstant.APP_CLOSE, message);
				vertx.eventBus().send(AddressConstant.APP_START, message);
			} catch (Exception e) {
				LOG.warn("app message is error", e);
			}
		});
		dList.forEach(kv -> {
			try {
				JsonObject message = new JsonObject(kv.getValue());
				vertx.eventBus().send(AddressConstant.APP_CLOSE, message);
			} catch (Exception e) {
				LOG.warn("app message is error", e);
			}
		});
		cList.forEach(kv -> {
			try {
				JsonObject message = new JsonObject(kv.getValue());
				vertx.eventBus().send(AddressConstant.APP_START, message);
			} catch (Exception e) {
				LOG.warn("app message is error", e);
			}
		});

	}

	private Future<String> deploy(String verticleName, DeploymentOptions deployOptions) {
		Promise<String> promise = Promise.promise();
		vertx.deployVerticle(verticleName, deployOptions, promise);
		return promise.future();
	}

}