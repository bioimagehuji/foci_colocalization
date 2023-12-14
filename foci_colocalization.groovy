// Colocalization of foci (spots) with QuPath
// Usage: Create an Annotation ROI and run the script.


// Create an Annotation and select it
    def plane = ImagePlane.getPlane(0, 0)
    pixel_size_xy = 0.3
    def full_annotation = false
    
    def roi
    def fitc_annotation 
    if (full_annotation) {
        clearAllObjects();
        createFullImageAnnotation(true)
        fitc_annotation = getSelectedObject()
    }
    else {
    //    def roi = ROIs.createRectangleROI((int)(900/pixel_size_xy), (int)(350/pixel_size_xy), 1000/pixel_size_xy, 1000/pixel_size_xy, plane)
        roi = getSelectedROI()
        fitc_annotation = PathObjects.createAnnotationObject(roi)
        addObject(fitc_annotation)
        //fitc_annotation.setPathClass(getPathClass("fitc_foci"))
        }
    fitc_annotation.setName("fitc_ann")
    
    def cy5_annotation 
    if (full_annotation) {
        createFullImageAnnotation(true)
        cy5_annotation = getSelectedObject()
    }
    else {
        //roi = ROIs.createRectangleROI((int)(1000/pixel_size_xy), (int)(400/pixel_size_xy), 1000/pixel_size_xy, 1000/pixel_size_xy, plane)
        roi = getSelectedROI()
        cy5_annotation = PathObjects.createAnnotationObject(roi)
        addObject(cy5_annotation)
        //cy5_annotation.setPathClass(getPathClass("cy5_foci"))
    }
    cy5_annotation.setName("cy5_ann")
    
// Detect foci
    selectObjects(fitc_annotation)
    print("Finding FITC foci")
    createDetectionsFromPixelClassifier("fitc_thresh_laplace5", 0.1, 0.0, "SPLIT")
    selectObjects(cy5_annotation)
    print("Finding Cy5 foci")
    createDetectionsFromPixelClassifier("cy5_thresh_laplace", 0.1, 0.0, "SPLIT")

// Filter out large foci
    selectObjectsByClassification("cy5_foci");
    addShapeMeasurements("AREA")
    selectObjectsByClassification("fitc_foci");
    addShapeMeasurements("AREA")
    resetSelection()

    fitc_detections = getDetectionObjects().findAll { it.getPathClass() == getPathClass("fitc_foci")}
    print("fitc: " + fitc_detections.size() )
    def delete_fitc = fitc_detections.findAll{ measurement(it, "Area µm^2") > 0.7 }
    print("delete fitc: " + delete_fitc.size() )
    removeObjects(delete_fitc, true)
    fitc_detections = getDetectionObjects().findAll { it.getPathClass() == getPathClass("fitc_foci")}
    print("fitc: " + fitc_detections.size() )
    
    cy5_detections = getDetectionObjects().findAll { it.getPathClass() == getPathClass("cy5_foci")}
    print("cy5: " + cy5_detections.size() )
    def delete_cy5 = cy5_detections.findAll{  measurement(it, "Area µm^2") > 0.7 }
    print("delete cy5: " + delete_cy5.size() )
    removeObjects(delete_cy5, true)
    cy5_detections = getDetectionObjects().findAll { it.getPathClass() == getPathClass("cy5_foci")}
    print("cy5: " + fitc_detections.size() )
    
// Calculate distances
    detectionCentroidDistances(false)

// Colocalization: Find Cy5 and FITC foci which are close enough to each other. 
    colocalization_distance = 0.3
    
    def cy5_coloc = cy5_detections.findAll { measurement(it, "Distance to detection fitc_foci µm") <= colocalization_distance  }
    print("cy5 coloc: " + cy5_coloc.size() )
    cy5_coloc_class = getPathClass("cy5_coloc")
    cy5_coloc.forEach { it.setPathClass(cy5_coloc_class)}
    
    def fitc_coloc = fitc_detections.findAll { measurement(it, "Distance to detection cy5_foci µm") <= colocalization_distance  }
    print("fitc coloc: " + fitc_coloc.size() )
    fitc_coloc_class = getPathClass("fitc_coloc")
    fitc_coloc.forEach { it.setPathClass(fitc_coloc_class)}
    
// Statistics
    print("CY5 colocalized/all:  " + cy5_coloc.size()  + "/" + cy5_detections.size()  + " = " + (cy5_coloc.size() / cy5_detections.size()* 100)  + "%")
    print("FITC colocalized/all: " + fitc_coloc.size() + "/" + fitc_detections.size() + " = " + (fitc_coloc.size() / fitc_detections.size()* 100)  + "%")
    