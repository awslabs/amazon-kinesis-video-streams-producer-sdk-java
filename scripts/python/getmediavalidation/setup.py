from setuptools import setup, find_packages

# Dynamically read the requirements.txt file
with open('requirements.txt') as file:
    # Remove comments in the file
    requirements = [line.strip() for line in file if line.strip() and not line.startswith('#')]

setup(
    name="getmediautils",
    version="0.0.1",
    description="Tools for downloading and validating Amazon Kinesis Video Stream media",
    author="Jeremy Gunawan",
    author_email="jggunawa@amazon.com",
    packages=find_packages(),
    python_requires=">=3.12",
    install_requires=requirements,
    classifiers=[
        "Development Status :: 3 - Alpha",
        "Intended Audience :: Developers",
        "Programming Language :: Python :: 3.12",
    ],
)

