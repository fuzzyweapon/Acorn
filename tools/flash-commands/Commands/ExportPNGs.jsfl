﻿/*
 * Copyright 2015 Poly Forest, LLC
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
 
fl.outputPanel.clear();
var doc = fl.getDocumentDOM();
var library = document.library;
var log = [];

if (!doc) {
	alert("Select a document.");
} else {	
	var docName = doc.name.substring(0, doc.name.length - 4);
	var folder = doc.pathURI.substring(0, doc.pathURI.lastIndexOf(doc.name));
	
	// Create a temporary doc.
	fl.createDocument();
	var exportDoc = fl.getDocumentDOM();
	
	trace("Publishing pngs to: " + folder);
	for each (var item in library.items) {
		if (item.linkageExportForAS) {
			makeDirs(item.name);
			trace("Item: " + item.name);
			var totalFrames = item.timeline.layers[0].frames.length;
			var timeline = exportDoc.timelines[0];
			timeline.removeFrames(0, timeline.frameCount);
			timeline.insertFrames(totalFrames);
			exportDoc.addItem({ x: 0, y: 0 }, item);
			
			exportDoc.timelines[0].layers[0].frames[0].elements[0].symbolType = "graphic";
			resizeDocument(exportDoc, item);
			var lastLabel = null;
			var sameLabelCount = 0;
			for (var i = 0; i < totalFrames; i++) {
				exportDoc.timelines[0].setSelectedFrames(i, i);
				var pngName;
				var frame = item.timeline.layers[0].frames[i];
				if (frame.labelType == "name") {
					if (lastLabel != frame.name) {
						lastLabel = frame.name;
					} else {
						sameLabelCount++;
					}
					pngName = folder + item.name + "_" + frame.name + numberTag(sameLabelCount) + ".png";
				} else {
					pngName = folder + item.name + numberTag(i) + ".png";
				}
				exportDoc.exportPNG(pngName, true, true);
			}
			exportDoc.timelines[0].setSelectedFrames(0, 0);
		}
	}
	
	exportDoc.close(false);
	fl.outputPanel.clear();
	fl.trace(log.join("\n"));

}

function numberTag(i) {
	if (i == 0) return "";
	else return "_" + padNumber(i, 4);
}

function trace(message) {
	log.push(message);
	if (log.length > 1000) log.unshift();
	fl.trace(message);
}

function makeDirs(path) {
	var split = path.split("/");
	var parent = folder;
	for (var i = 0; i < split.length - 1; i++) {
		parent += split[i] + "/";
		if (!FLfile.exists(parent)) {
			FLfile.createFolder(parent);
		}
	}
}

function padNumber(num, digits) {
	var str = num + "";
	var intDiff = digits - str.length;
	while (intDiff > 0) {
		str = "0" + str;
		intDiff--;
	}
	return str;
}

function resizeDocument(doc, item) {
	doc.selectAll();
	var element = doc.timelines[0].layers[0].frames[0].elements[0];
	if (element == null) return false;
	element.x += -element.left;
	element.y += -element.top;
	doc.width = Math.ceil(element.width);
	doc.height = Math.ceil(element.height);	
	return true;
}
