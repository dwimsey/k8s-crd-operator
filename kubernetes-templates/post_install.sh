#!/bin/sh

oadm policy add-cluster-role-to-user view system:serviceaccount:ops:crd-operator-{{ ocp_application_name }}
